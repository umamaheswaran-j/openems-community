package io.openems.edge.goodwe.charger;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.goodwe.common.GoodWe;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

public abstract class AbstractGoodWeEtCharger extends AbstractOpenemsModbusComponent
		implements GoodWeEtCharger, EssDcCharger, ModbusComponent, OpenemsComponent, TimedataProvider, EventHandler {

	protected abstract GoodWe getEssOrBatteryInverter();

	private final CalculateEnergyFromPower calculateActualEnergy = new CalculateEnergyFromPower(this,
			EssDcCharger.ChannelId.ACTUAL_ENERGY);

	protected AbstractGoodWeEtCharger() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				EssDcCharger.ChannelId.values(), //
				GoodWeEtCharger.ChannelId.values() //
		);
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {
		final int startAddress = this.getStartAddress();
		return new ModbusProtocol(this, //
				new FC3ReadRegistersTask(startAddress, Priority.HIGH, //
						m(GoodWeEtCharger.ChannelId.V, new UnsignedWordElement(startAddress), //
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1), //
						m(GoodWeEtCharger.ChannelId.I, new UnsignedWordElement(startAddress + 1),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1),
						m(EssDcCharger.ChannelId.ACTUAL_POWER, new UnsignedDoublewordElement(startAddress + 2))));
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculateEnergy();
			this.updateState();
			break;
		}
	}

	/**
	 * Calculate the Energy values from ActivePower.
	 */
	private void calculateEnergy() {
		Integer actualPower = this.getActualPower().get();
		if (actualPower == null) {
			// Not available
			this.calculateActualEnergy.update(null);
		} else if (actualPower > 0) {
			this.calculateActualEnergy.update(actualPower);
		} else {
			this.calculateActualEnergy.update(0);
		}
	}

	/**
	 * Updates the 'Has-No-DC-PV' State Channel.
	 */
	private void updateState() {
		GoodWe goodWe = this.getEssOrBatteryInverter();
		Boolean hasNoDcPv = null;
		if (goodWe != null) {
			switch (goodWe.getGoodweType().getSeries()) {
			case BT:
				hasNoDcPv = true;
				break;
			case ET:
				hasNoDcPv = false;
				break;
			case UNDEFINED:
				hasNoDcPv = null;
				break;
			}
		}
		this._setHasNoDcPv(hasNoDcPv);
	}

	@Override
	public final String debugLog() {
		return "L:" + this.getActualPower().asString();
	}

	protected abstract int getStartAddress();
}

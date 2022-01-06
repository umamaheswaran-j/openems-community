package io.openems.edge.ess.adstec.storaxe;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverterChain;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Ess.Adstec.StoraXe", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)
public class AdstecStoraxeEssImpl extends AbstractOpenemsModbusComponent
		implements SymmetricEss, OpenemsComponent, ModbusComponent, ModbusSlave {

	@Reference
	protected ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public AdstecStoraxeEssImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				AsymmetricEss.ChannelId.values(), //
				AdstecStoraxeEss.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm, "Modbus",
				config.modbus_id());
		this._setCapacity(config.capacity());
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {
		final int offset = -1; // The Modbus library seems to use 0 offsets.

		// We need to override because the ess returns neg for capacitative, pos for
		// inductive, but the channel expects
		// pos for from-battery, neg for to-battery.
		final ElementToChannelConverter reactivePowerConverter = new ElementToChannelConverter(
				value -> Math.abs((Short) value) * Integer.signum(this.getActivePower().get()));

		return new ModbusProtocol(this, //
				new FC4ReadInputRegistersTask(1 + offset, Priority.LOW, //
						m(SymmetricEss.ChannelId.GRID_MODE, new UnsignedWordElement(1 + offset), GRID_MODE_CONVERTER),
						m(SymmetricEss.ChannelId.ACTIVE_POWER, new SignedWordElement(2 + offset),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(SymmetricEss.ChannelId.REACTIVE_POWER, new SignedWordElement(3 + offset),
								new ElementToChannelConverterChain(ElementToChannelConverter.SCALE_FACTOR_2,
										reactivePowerConverter))),
				new FC4ReadInputRegistersTask(125 + offset, Priority.LOW, //
						m(SymmetricEss.ChannelId.MAX_APPARENT_POWER, new UnsignedWordElement(125 + offset),
								ElementToChannelConverter.SCALE_FACTOR_2),
						m(SymmetricEss.ChannelId.SOC, new UnsignedWordElement(126 + offset),
								ElementToChannelConverter.DIRECT_1_TO_1)),
				new FC4ReadInputRegistersTask(134 + offset, Priority.LOW, //
						m(SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY, new UnsignedDoublewordElement(134 + offset),
								ElementToChannelConverter.SCALE_FACTOR_3),
						m(SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY, new UnsignedDoublewordElement(136 + offset),
								ElementToChannelConverter.SCALE_FACTOR_3),
						m(SymmetricEss.ChannelId.MIN_CELL_VOLTAGE, new UnsignedWordElement(138 + offset),
								ElementToChannelConverter.DIRECT_1_TO_1),
						m(SymmetricEss.ChannelId.MAX_CELL_VOLTAGE, new UnsignedWordElement(139 + offset),
								ElementToChannelConverter.DIRECT_1_TO_1)));
	}

	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() + "|L:" + this.getActivePower().asString();
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				SymmetricEss.getModbusSlaveNatureTable(accessMode), //
				AsymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(AdstecStoraxeEss.class, accessMode, 300) //
						.build());
	}

	private static final ElementToChannelConverter GRID_MODE_CONVERTER = new ElementToChannelConverter(value -> {
		switch ((Integer) value) {
		case 1:
			return GridMode.OFF_GRID;
		case 2:
			return GridMode.ON_GRID;
		default:
			return GridMode.UNDEFINED;
		}
	});
}
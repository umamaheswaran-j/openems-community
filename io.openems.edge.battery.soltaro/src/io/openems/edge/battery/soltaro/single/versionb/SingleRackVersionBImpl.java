package io.openems.edge.battery.soltaro.single.versionb;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.protection.BatteryProtection;
import io.openems.edge.battery.soltaro.common.ChannelIdImpl;
import io.openems.edge.battery.soltaro.common.batteryprotection.BatteryProtectionDefinitionSoltaro3000Wh;
import io.openems.edge.battery.soltaro.common.batteryprotection.BatteryProtectionDefinitionSoltaro3500Wh;
import io.openems.edge.battery.soltaro.common.enums.ModuleType;
import io.openems.edge.battery.soltaro.single.versionb.statemachine.Context;
import io.openems.edge.battery.soltaro.single.versionb.statemachine.ControlAndLogic;
import io.openems.edge.battery.soltaro.single.versionb.statemachine.StateMachine;
import io.openems.edge.battery.soltaro.single.versionb.statemachine.StateMachine.State;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.ModbusUtils;
import io.openems.edge.bridge.modbus.api.element.AbstractModbusElement;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Bms.Soltaro.SingleRack.VersionB", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
		})
public class SingleRackVersionBImpl extends AbstractOpenemsModbusComponent implements Battery, ModbusComponent,
		OpenemsComponent, EventHandler, ModbusSlave, StartStoppable, SingleRackVersionB {

	@Reference
	protected ConfigurationAdmin cm;

	@Reference
	protected ComponentManager componentManager;

	private AtomicReference<StartStop> startStopTarget = new AtomicReference<StartStop>(StartStop.UNDEFINED);

	/**
	 * Manages the {@link State}s of the StateMachine.
	 */
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final Logger log = LoggerFactory.getLogger(SingleRackVersionBImpl.class);

	private Config config;
	private BatteryProtection batteryProtection = null;
	private Optional<Integer> numberOfModules = Optional.empty();

	public SingleRackVersionBImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				Battery.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				SingleRackVersionB.ChannelId.values(), //
				BatteryProtection.ChannelId.values() //
		);
	}

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	@Activate
	protected void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}

		// Initialize Battery-Protection
		if (config.moduleType() == ModuleType.MODULE_3_5_KWH) {
			// Special settings for 3.5 kWh module
			this.batteryProtection = BatteryProtection.create(this) //
					.applyBatteryProtectionDefinition(new BatteryProtectionDefinitionSoltaro3500Wh(),
							this.componentManager) //
					.build();
		} else {
			// Default
			this.batteryProtection = BatteryProtection.create(this) //
					.applyBatteryProtectionDefinition(new BatteryProtectionDefinitionSoltaro3000Wh(),
							this.componentManager) //
					.build();
		}

		ControlAndLogic.setWatchdog(this, config.watchdog());
		ControlAndLogic.setSoCLowAlarm(this, config.SoCLowAlarm());

		this.getNumberOfModules().thenAccept(numberOfModules -> {
			this.numberOfModules = Optional.of(numberOfModules);
			this.calculateCapacity(numberOfModules);
			this.createDynamicChannels(numberOfModules);
		});
	}

	private void handleStateMachine() {
		// Store the current State
		this.channel(SingleRackVersionB.ChannelId.STATE_MACHINE).setNextValue(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		// Prepare Context
		Context context = new Context(this, this.config, this.numberOfModules);

		// Call the StateMachine
		try {
			this.stateMachine.run(context);

			this.channel(SingleRackVersionB.ChannelId.RUN_FAILED).setNextValue(false);

		} catch (OpenemsNamedException e) {
			this.channel(SingleRackVersionB.ChannelId.RUN_FAILED).setNextValue(true);
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {

		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			// TODO set soltaro protect/recover registers
			this.batteryProtection.apply();
			break;

		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.handleStateMachine();
			break;
		}
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				Battery.getModbusSlaveNatureTable(accessMode) //
		);
	}

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			// Set only if value changed
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}

	@Override
	public StartStop getStartStopTarget() {
		switch (this.config.startStop()) {
		case AUTO:
			// read StartStop-Channel
			return this.startStopTarget.get();

		case START:
			// force START
			return StartStop.START;

		case STOP:
			// force STOP
			return StartStop.STOP;
		}

		assert false;
		return StartStop.UNDEFINED; // can never happen
	}

	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc() //
				+ "|Discharge:" + this.getDischargeMinVoltage() + ";" + this.getDischargeMaxCurrent() //
				+ "|Charge:" + this.getChargeMaxVoltage() + ";" + this.getChargeMaxCurrent() //
				+ "|Cell Voltages: Min:" + this.getMinCellVoltage() + "; Max:" + this.getMaxCellVoltage() //
				+ "|State:" + this.stateMachine.getCurrentState().asCamelCase();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {

		ModbusProtocol protocol = new ModbusProtocol(this, //
				// Main switch
				new FC6WriteRegisterTask(0x2010,
						m(SingleRackVersionB.ChannelId.BMS_CONTACTOR_CONTROL, new UnsignedWordElement(0x2010)) //
				),

				// System reset
				new FC6WriteRegisterTask(0x2004, //
						m(SingleRackVersionB.ChannelId.SYSTEM_RESET, new UnsignedWordElement(0x2004)) //
				),

				// EMS timeout --> Watchdog
				new FC6WriteRegisterTask(0x201C, //
						m(SingleRackVersionB.ChannelId.EMS_COMMUNICATION_TIMEOUT, new UnsignedWordElement(0x201C)) //
				),
				// Sleep
				new FC6WriteRegisterTask(0x201D, //
						m(SingleRackVersionB.ChannelId.SLEEP, new UnsignedWordElement(0x201D)) //
				),

				// Work parameter
				new FC6WriteRegisterTask(0x20C1, //
						m(SingleRackVersionB.ChannelId.WORK_PARAMETER_NUMBER_OF_MODULES,
								new UnsignedWordElement(0x20C1)) //
				), //

				// Paramaeters for configuring
				new FC6WriteRegisterTask(0x2014,
						m(SingleRackVersionB.ChannelId.AUTO_SET_SLAVES_ID, new UnsignedWordElement(0x2014))),
				new FC6WriteRegisterTask(0x2019,
						m(SingleRackVersionB.ChannelId.AUTO_SET_SLAVES_TEMPERATURE_ID,
								new UnsignedWordElement(0x2019))),

				// Control registers
				new FC3ReadRegistersTask(0x2000, Priority.HIGH, //
						m(SingleRackVersionB.ChannelId.FAN_STATUS, new UnsignedWordElement(0x2000)), //
						m(SingleRackVersionB.ChannelId.MAIN_CONTACTOR_STATE, new UnsignedWordElement(0x2001)), //
						m(SingleRackVersionB.ChannelId.DRY_CONTACT_1_EXPORT, new UnsignedWordElement(0x2002)), //
						m(SingleRackVersionB.ChannelId.DRY_CONTACT_2_EXPORT, new UnsignedWordElement(0x2003)), //
						m(SingleRackVersionB.ChannelId.SYSTEM_RESET, new UnsignedWordElement(0x2004)), //
						m(SingleRackVersionB.ChannelId.SYSTEM_RUN_MODE, new UnsignedWordElement(0x2005)), //
						m(SingleRackVersionB.ChannelId.PRE_CONTACTOR_STATUS, new UnsignedWordElement(0x2006)), //
						m(new BitsWordElement(0x2007, this) //
								.bit(15, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_DISCHARGE_TEMPERATURE_LOW) //
								.bit(14, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_DISCHARGE_TEMPERATURE_HIGH) //
								.bit(13, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_VOLTAGE_DIFFERENCE) //
								.bit(12, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_INSULATION_LOW) //
								.bit(11, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_CELL_VOLTAGE_DIFFERENCE) //
								.bit(10, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_ELECTRODE_TEMPERATURE_HIGH) //
								.bit(9, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_TEMPERATURE_DIFFERENCE) //
								.bit(8, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_SOC_LOW) //
								.bit(7, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_CELL_OVER_TEMPERATURE) //
								.bit(6, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_CELL_LOW_TEMPERATURE) //
								.bit(5, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_DISCHARGE_OVER_CURRENT) //
								.bit(4, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_SYSTEM_LOW_VOLTAGE) //
								.bit(3, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_CELL_LOW_VOLTAGE) //
								.bit(2, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_CHARGE_OVER_CURRENT) //
								.bit(1, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_SYSTEM_OVER_VOLTAGE) //
								.bit(0, SingleRackVersionB.ChannelId.ALARM_FLAG_STATUS_CELL_OVER_VOLTAGE) //
						), //
						m(new BitsWordElement(0x2008, this) //
								.bit(15, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_DISCHARGE_TEMPERATURE_LOW) //
								.bit(14, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_DISCHARGE_TEMPERATURE_HIGH) //
								.bit(13, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_VOLTAGE_DIFFERENCE) //
								.bit(12, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_INSULATION_LOW) //
								.bit(11, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_CELL_VOLTAGE_DIFFERENCE) //
								.bit(10, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_ELECTRODE_TEMPERATURE_HIGH) //
								.bit(9, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_TEMPERATURE_DIFFERENCE) //
								.bit(8, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_SOC_LOW) //
								.bit(7, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_CELL_OVER_TEMPERATURE) //
								.bit(6, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_CELL_LOW_TEMPERATURE) //
								.bit(5, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_DISCHARGE_OVER_CURRENT) //
								.bit(4, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_SYSTEM_LOW_VOLTAGE) //
								.bit(3, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_CELL_LOW_VOLTAGE) //
								.bit(2, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_CHARGE_OVER_CURRENT) //
								.bit(1, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_SYSTEM_OVER_VOLTAGE) //
								.bit(0, SingleRackVersionB.ChannelId.PROTECT_FLAG_STATUS_CELL_OVER_VOLTAGE) //
						), //
						m(SingleRackVersionB.ChannelId.ALARM_FLAG_REGISTER_1, new UnsignedWordElement(0x2009)), //
						m(SingleRackVersionB.ChannelId.ALARM_FLAG_REGISTER_2, new UnsignedWordElement(0x200A)), //
						m(SingleRackVersionB.ChannelId.PROTECT_FLAG_REGISTER_1, new UnsignedWordElement(0x200B)), //
						m(SingleRackVersionB.ChannelId.PROTECT_FLAG_REGISTER_2, new UnsignedWordElement(0x200C)), //
						m(SingleRackVersionB.ChannelId.SHORT_CIRCUIT_FUNCTION, new UnsignedWordElement(0x200D)), //
						m(SingleRackVersionB.ChannelId.TESTING_IO, new UnsignedWordElement(0x200E)), //
						m(SingleRackVersionB.ChannelId.SOFT_SHUTDOWN, new UnsignedWordElement(0x200F)), //
						m(SingleRackVersionB.ChannelId.BMS_CONTACTOR_CONTROL, new UnsignedWordElement(0x2010)), //
						m(SingleRackVersionB.ChannelId.CURRENT_BOX_SELF_CALIBRATION, new UnsignedWordElement(0x2011)), //
						m(SingleRackVersionB.ChannelId.PCS_ALARM_RESET, new UnsignedWordElement(0x2012)), //
						m(SingleRackVersionB.ChannelId.INSULATION_SENSOR_FUNCTION, new UnsignedWordElement(0x2013)), //
						m(SingleRackVersionB.ChannelId.AUTO_SET_SLAVES_ID, new UnsignedWordElement(0x2014)), //
						new DummyRegisterElement(0x2015, 0x2018), //
						m(SingleRackVersionB.ChannelId.AUTO_SET_SLAVES_TEMPERATURE_ID, new UnsignedWordElement(0x2019)), //
						m(SingleRackVersionB.ChannelId.TRANSPARENT_MASTER, new UnsignedWordElement(0x201A)), //
						m(SingleRackVersionB.ChannelId.SET_EMS_ADDRESS, new UnsignedWordElement(0x201B)), //
						m(SingleRackVersionB.ChannelId.EMS_COMMUNICATION_TIMEOUT, new UnsignedWordElement(0x201C)), //
						m(SingleRackVersionB.ChannelId.SLEEP, new UnsignedWordElement(0x201D)), //
						m(SingleRackVersionB.ChannelId.VOLTAGE_LOW_PROTECTION, new UnsignedWordElement(0x201E)) //
				), //

				// Voltage ranges
				new FC3ReadRegistersTask(0x2082, Priority.LOW, //
						m(new UnsignedWordElement(0x2082)) //
								.m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_OVER_VOLTAGE_ALARM,
										ElementToChannelConverter.SCALE_FACTOR_2) //
								.m(Battery.ChannelId.CHARGE_MAX_VOLTAGE, ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
								.build(), //
						new DummyRegisterElement(0x2083, 0x2087), //
						m(new UnsignedWordElement(0x2088)) //
								.m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_UNDER_VOLTAGE_ALARM,
										ElementToChannelConverter.SCALE_FACTOR_2) //
								.m(Battery.ChannelId.DISCHARGE_MIN_VOLTAGE,
										ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
								.build() //
				),

				// Summary state
				new FC3ReadRegistersTask(0x2100, Priority.HIGH, //
						m(new UnsignedWordElement(0x2100)) //
								.m(SingleRackVersionB.ChannelId.CLUSTER_1_VOLTAGE,
										ElementToChannelConverter.SCALE_FACTOR_2) //
								.m(Battery.ChannelId.VOLTAGE, ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
								.build(), //
						m(new SignedWordElement(0x2101)) //
								.m(SingleRackVersionB.ChannelId.CLUSTER_1_CURRENT,
										ElementToChannelConverter.SCALE_FACTOR_2) //
								.m(Battery.ChannelId.CURRENT, ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
								.build(), //
						m(SingleRackVersionB.ChannelId.CHARGE_INDICATION, new UnsignedWordElement(0x2102)),
						m(Battery.ChannelId.SOC, new UnsignedWordElement(0x2103)),
						m(SingleRackVersionB.ChannelId.CLUSTER_1_SOH, new UnsignedWordElement(0x2104)),
						m(SingleRackVersionB.ChannelId.CLUSTER_1_MAX_CELL_VOLTAGE_ID, new UnsignedWordElement(0x2105)), //
						m(new SignedWordElement(0x2106)) //
								.m(SingleRackVersionB.ChannelId.CLUSTER_1_MAX_CELL_VOLTAGE,
										ElementToChannelConverter.DIRECT_1_TO_1) //
								.m(Battery.ChannelId.MAX_CELL_VOLTAGE, ElementToChannelConverter.DIRECT_1_TO_1) //
								.build(), //
						m(SingleRackVersionB.ChannelId.CLUSTER_1_MIN_CELL_VOLTAGE_ID, new UnsignedWordElement(0x2107)), //
						m(new UnsignedWordElement(0x2108)) //
								.m(SingleRackVersionB.ChannelId.CLUSTER_1_MIN_CELL_VOLTAGE,
										ElementToChannelConverter.DIRECT_1_TO_1) //
								.m(Battery.ChannelId.MIN_CELL_VOLTAGE, ElementToChannelConverter.DIRECT_1_TO_1) //
								.build(), //
						m(SingleRackVersionB.ChannelId.CLUSTER_1_MAX_CELL_TEMPERATURE_ID,
								new UnsignedWordElement(0x2109)), //
						m(new SignedWordElement(0x210A)) //
								.m(SingleRackVersionB.ChannelId.CLUSTER_1_MAX_CELL_TEMPERATURE,
										ElementToChannelConverter.DIRECT_1_TO_1) //
								.m(Battery.ChannelId.MAX_CELL_TEMPERATURE,
										ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
								.build(), //
						m(SingleRackVersionB.ChannelId.CLUSTER_1_MIN_CELL_TEMPERATURE_ID,
								new UnsignedWordElement(0x210B)), //
						m(new SignedWordElement(0x210C)) //
								.m(SingleRackVersionB.ChannelId.CLUSTER_1_MIN_CELL_TEMPERATURE,
										ElementToChannelConverter.DIRECT_1_TO_1) //
								.m(Battery.ChannelId.MIN_CELL_TEMPERATURE,
										ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
								.build(), //
						m(SingleRackVersionB.ChannelId.MAX_CELL_RESISTANCE_ID, new UnsignedWordElement(0x210D)), //
						m(SingleRackVersionB.ChannelId.MAX_CELL_RESISTANCE, new UnsignedWordElement(0x210E),
								ElementToChannelConverter.SCALE_FACTOR_1), //
						m(SingleRackVersionB.ChannelId.MIN_CELL_RESISTANCE_ID, new UnsignedWordElement(0x210F)), //
						m(SingleRackVersionB.ChannelId.MIN_CELL_RESISTANCE, new UnsignedWordElement(0x2110),
								ElementToChannelConverter.SCALE_FACTOR_1), //
						m(SingleRackVersionB.ChannelId.POSITIVE_INSULATION, new UnsignedWordElement(0x2111)), //
						m(SingleRackVersionB.ChannelId.NEGATIVE_INSULATION, new UnsignedWordElement(0x2112)), //
						m(SingleRackVersionB.ChannelId.MAIN_CONTACTOR_FLAG, new UnsignedWordElement(0x2113)), //
						new DummyRegisterElement(0x2114),
						m(SingleRackVersionB.ChannelId.ENVIRONMENT_TEMPERATURE, new UnsignedWordElement(0x2115)), //
						m(SingleRackVersionB.ChannelId.SYSTEM_INSULATION, new UnsignedWordElement(0x2116)), //
						m(SingleRackVersionB.ChannelId.CELL_VOLTAGE_DIFFERENCE, new UnsignedWordElement(0x2117)), //
						m(SingleRackVersionB.ChannelId.TOTAL_VOLTAGE_DIFFERENCE, new UnsignedWordElement(0x2118),
								ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.POWER_TEMPERATURE, new UnsignedWordElement(0x2119)), //
						m(SingleRackVersionB.ChannelId.POWER_SUPPLY_VOLTAGE, new UnsignedWordElement(0x211A)) //
				),

				// Critical state
				new FC3ReadRegistersTask(0x2140, Priority.HIGH, //
						m(new BitsWordElement(0x2140, this) //
								.bit(0, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_VOLTAGE_HIGH) //
								.bit(1, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_TOTAL_VOLTAGE_HIGH) //
								.bit(2, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CHA_CURRENT_HIGH) //
								.bit(3, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_VOLTAGE_LOW) //
								.bit(4, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_TOTAL_VOLTAGE_LOW) //
								.bit(5, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_DISCHA_CURRENT_HIGH) //
								.bit(6, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_CHA_TEMP_HIGH) //
								.bit(7, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_CHA_TEMP_LOW) //
								.bit(8, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_SOC_LOW) //
								.bit(9, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_TEMPERATURE_DIFFERENCE_HIGH) //
								.bit(10, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_POLES_TEMPERATURE_DIFFERENCE_HIGH) //
								.bit(11, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_VOLTAGE_DIFFERENCE_HIGH) //
								.bit(12, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_INSULATION_LOW) //
								.bit(13, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_TOTAL_VOLTAGE_DIFFERENCE_HIGH) //
								.bit(14, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_DISCHA_TEMP_HIGH) //
								.bit(15, SingleRackVersionB.ChannelId.ALARM_LEVEL_2_CELL_DISCHA_TEMP_LOW) //
						), //
						m(new BitsWordElement(0x2141, this) //
								.bit(0, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_VOLTAGE_HIGH) //
								.bit(1, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_TOTAL_VOLTAGE_HIGH) //
								.bit(2, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CHA_CURRENT_HIGH) //
								.bit(3, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_VOLTAGE_LOW) //
								.bit(4, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_TOTAL_VOLTAGE_LOW) //
								.bit(5, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_DISCHA_CURRENT_HIGH) //
								.bit(6, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_CHA_TEMP_HIGH) //
								.bit(7, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_CHA_TEMP_LOW) //
								.bit(8, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_SOC_LOW) //
								.bit(9, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_TEMP_DIFF_HIGH) //
								.bit(10, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_POLE_TEMPERATURE_TOO_HIGH) //
								.bit(11, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_VOLTAGE_DIFF_HIGH) //
								.bit(12, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_INSULATION_LOW) //
								.bit(13, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_TOTAL_VOLTAGE_DIFF_HIGH) //
								.bit(14, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_DISCHA_TEMP_HIGH) //
								.bit(15, SingleRackVersionB.ChannelId.ALARM_LEVEL_1_CELL_DISCHA_TEMP_LOW) //
						), //
						m(SingleRackVersionB.ChannelId.CLUSTER_RUN_STATE, new UnsignedWordElement(0x2142)), //

						m(SingleRackVersionB.ChannelId.MAXIMUM_CELL_VOLTAGE_NUMBER_WHEN_ALARM,
								new UnsignedWordElement(0x2143)), //
						m(SingleRackVersionB.ChannelId.MAXIMUM_CELL_VOLTAGE_WHEN_ALARM,
								new UnsignedWordElement(0x2144)), //
						m(SingleRackVersionB.ChannelId.MAXIMUM_CELL_VOLTAGE_NUMBER_WHEN_STOPPED,
								new UnsignedWordElement(0x2145)), //
						m(SingleRackVersionB.ChannelId.MAXIMUM_CELL_VOLTAGE_WHEN_STOPPED,
								new UnsignedWordElement(0x2146)), //
						m(SingleRackVersionB.ChannelId.MINIMUM_CELL_VOLTAGE_NUMBER_WHEN_ALARM,
								new UnsignedWordElement(0x2147)), //
						m(SingleRackVersionB.ChannelId.MINIMUM_CELL_VOLTAGE_WHEN_ALARM,
								new UnsignedWordElement(0x2148)), //
						m(SingleRackVersionB.ChannelId.MINIMUM_CELL_VOLTAGE_NUMBER_WHEN_STOPPED,
								new UnsignedWordElement(0x2149)), //
						m(SingleRackVersionB.ChannelId.MINIMUM_CELL_VOLTAGE_WHEN_STOPPED,
								new UnsignedWordElement(0x214A)), //
						m(SingleRackVersionB.ChannelId.OVER_VOLTAGE_VALUE_WHEN_ALARM, new UnsignedWordElement(0x214B)), //
						m(SingleRackVersionB.ChannelId.OVER_VOLTAGE_VALUE_WHEN_STOPPED,
								new UnsignedWordElement(0x214C)), //
						m(SingleRackVersionB.ChannelId.UNDER_VOLTAGE_VALUE_WHEN_ALARM, new UnsignedWordElement(0x214D)), //
						m(SingleRackVersionB.ChannelId.UNDER_VOLTAGE_VALUE_WHEN_STOPPED,
								new UnsignedWordElement(0x214E)), //
						m(SingleRackVersionB.ChannelId.OVER_CHARGE_CURRENT_WHEN_ALARM, new UnsignedWordElement(0x214F)), //
						m(SingleRackVersionB.ChannelId.OVER_CHARGE_CURRENT_WHEN_STOPPED,
								new UnsignedWordElement(0x2150)), //
						m(SingleRackVersionB.ChannelId.OVER_DISCHARGE_CURRENT_WHEN_ALARM,
								new UnsignedWordElement(0x2151)), //
						m(SingleRackVersionB.ChannelId.OVER_DISCHARGE_CURRENT_WHEN_STOPPED,
								new UnsignedWordElement(0x2152)), //
						m(SingleRackVersionB.ChannelId.NUMBER_OF_TEMPERATURE_WHEN_ALARM,
								new UnsignedWordElement(0x2153)), //
						new DummyRegisterElement(0x2154, 0x215A), //
						m(SingleRackVersionB.ChannelId.OTHER_ALARM_EQUIPMENT_FAILURE, new UnsignedWordElement(0x215B)), //
						new DummyRegisterElement(0x215C, 0x215F), //
						m(BatteryProtection.ChannelId.BP_CHARGE_BMS, new UnsignedWordElement(0x2160),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1), //
						m(BatteryProtection.ChannelId.BP_DISCHARGE_BMS, new UnsignedWordElement(0x2161),
								ElementToChannelConverter.SCALE_FACTOR_MINUS_1) //
				),
				// Cluster info
				new FC3ReadRegistersTask(0x2180, Priority.LOW, //
						m(SingleRackVersionB.ChannelId.CYCLE_TIME, new UnsignedWordElement(0x2180)), //
						m(SingleRackVersionB.ChannelId.TOTAL_CAPACITY_HIGH_BITS, new UnsignedWordElement(0x2181)), //
						m(SingleRackVersionB.ChannelId.TOTAL_CAPACITY_LOW_BITS, new UnsignedWordElement(0x2182)), //
						m(new BitsWordElement(0x2183, this) //
								.bit(3, SingleRackVersionB.ChannelId.SLAVE_20_COMMUNICATION_ERROR)//
								.bit(2, SingleRackVersionB.ChannelId.SLAVE_19_COMMUNICATION_ERROR)//
								.bit(1, SingleRackVersionB.ChannelId.SLAVE_18_COMMUNICATION_ERROR)//
								.bit(0, SingleRackVersionB.ChannelId.SLAVE_17_COMMUNICATION_ERROR)//
						), //
						m(new BitsWordElement(0x2184, this) //
								.bit(15, SingleRackVersionB.ChannelId.SLAVE_16_COMMUNICATION_ERROR)//
								.bit(14, SingleRackVersionB.ChannelId.SLAVE_15_COMMUNICATION_ERROR)//
								.bit(13, SingleRackVersionB.ChannelId.SLAVE_14_COMMUNICATION_ERROR)//
								.bit(12, SingleRackVersionB.ChannelId.SLAVE_13_COMMUNICATION_ERROR)//
								.bit(11, SingleRackVersionB.ChannelId.SLAVE_12_COMMUNICATION_ERROR)//
								.bit(10, SingleRackVersionB.ChannelId.SLAVE_11_COMMUNICATION_ERROR)//
								.bit(9, SingleRackVersionB.ChannelId.SLAVE_10_COMMUNICATION_ERROR)//
								.bit(8, SingleRackVersionB.ChannelId.SLAVE_9_COMMUNICATION_ERROR)//
								.bit(7, SingleRackVersionB.ChannelId.SLAVE_8_COMMUNICATION_ERROR)//
								.bit(6, SingleRackVersionB.ChannelId.SLAVE_7_COMMUNICATION_ERROR)//
								.bit(5, SingleRackVersionB.ChannelId.SLAVE_6_COMMUNICATION_ERROR)//
								.bit(4, SingleRackVersionB.ChannelId.SLAVE_5_COMMUNICATION_ERROR)//
								.bit(3, SingleRackVersionB.ChannelId.SLAVE_4_COMMUNICATION_ERROR)//
								.bit(2, SingleRackVersionB.ChannelId.SLAVE_3_COMMUNICATION_ERROR)//
								.bit(1, SingleRackVersionB.ChannelId.SLAVE_2_COMMUNICATION_ERROR)//
								.bit(0, SingleRackVersionB.ChannelId.SLAVE_1_COMMUNICATION_ERROR)//
						), //
						m(new BitsWordElement(0x2185, this) //
								.bit(0, SingleRackVersionB.ChannelId.FAILURE_SAMPLING_WIRE)//
								.bit(1, SingleRackVersionB.ChannelId.FAILURE_CONNECTOR_WIRE)//
								.bit(2, SingleRackVersionB.ChannelId.FAILURE_LTC6803)//
								.bit(3, SingleRackVersionB.ChannelId.FAILURE_VOLTAGE_SAMPLING)//
								.bit(4, SingleRackVersionB.ChannelId.FAILURE_TEMP_SAMPLING)//
								.bit(5, SingleRackVersionB.ChannelId.FAILURE_TEMP_SENSOR)//
								.bit(6, SingleRackVersionB.ChannelId.FAILURE_GR_T)//
								.bit(7, SingleRackVersionB.ChannelId.FAILURE_PCB)//
								.bit(8, SingleRackVersionB.ChannelId.FAILURE_BALANCING_MODULE)//
								.bit(9, SingleRackVersionB.ChannelId.FAILURE_TEMP_SAMPLING_LINE)//
								.bit(10, SingleRackVersionB.ChannelId.FAILURE_INTRANET_COMMUNICATION)//
								.bit(11, SingleRackVersionB.ChannelId.FAILURE_EEPROM)//
								.bit(12, SingleRackVersionB.ChannelId.FAILURE_INITIALIZATION)//
						), //
						m(SingleRackVersionB.ChannelId.SYSTEM_TIME_HIGH, new UnsignedWordElement(0x2186)), //
						m(SingleRackVersionB.ChannelId.SYSTEM_TIME_LOW, new UnsignedWordElement(0x2187)), //
						new DummyRegisterElement(0x2188, 0x218E), //
						m(SingleRackVersionB.ChannelId.LAST_TIME_CHARGE_CAPACITY_LOW_BITS,
								new UnsignedWordElement(0x218F), ElementToChannelConverter.SCALE_FACTOR_1), //
						m(SingleRackVersionB.ChannelId.LAST_TIME_CHARGE_END_TIME_HIGH_BITS,
								new UnsignedWordElement(0x2190)), //
						m(SingleRackVersionB.ChannelId.LAST_TIME_CHARGE_END_TIME_LOW_BITS,
								new UnsignedWordElement(0x2191)), //
						new DummyRegisterElement(0x2192), //
						m(SingleRackVersionB.ChannelId.LAST_TIME_DISCHARGE_CAPACITY_LOW_BITS,
								new UnsignedWordElement(0x2193), ElementToChannelConverter.SCALE_FACTOR_1), //
						m(SingleRackVersionB.ChannelId.LAST_TIME_DISCHARGE_END_TIME_HIGH_BITS,
								new UnsignedWordElement(0x2194)), //
						m(SingleRackVersionB.ChannelId.LAST_TIME_DISCHARGE_END_TIME_LOW_BITS,
								new UnsignedWordElement(0x2195)), //
						m(SingleRackVersionB.ChannelId.CELL_OVER_VOLTAGE_STOP_TIMES, new UnsignedWordElement(0x2196)), //
						m(SingleRackVersionB.ChannelId.BATTERY_OVER_VOLTAGE_STOP_TIMES,
								new UnsignedWordElement(0x2197)), //
						m(SingleRackVersionB.ChannelId.BATTERY_CHARGE_OVER_CURRENT_STOP_TIMES,
								new UnsignedWordElement(0x2198)), //
						m(SingleRackVersionB.ChannelId.CELL_VOLTAGE_LOW_STOP_TIMES, new UnsignedWordElement(0x2199)), //
						m(SingleRackVersionB.ChannelId.BATTERY_VOLTAGE_LOW_STOP_TIMES, new UnsignedWordElement(0x219A)), //
						m(SingleRackVersionB.ChannelId.BATTERY_DISCHARGE_OVER_CURRENT_STOP_TIMES,
								new UnsignedWordElement(0x219B)), //
						m(SingleRackVersionB.ChannelId.BATTERY_OVER_TEMPERATURE_STOP_TIMES,
								new UnsignedWordElement(0x219C)), //
						m(SingleRackVersionB.ChannelId.BATTERY_TEMPERATURE_LOW_STOP_TIMES,
								new UnsignedWordElement(0x219D)), //
						m(SingleRackVersionB.ChannelId.CELL_OVER_VOLTAGE_ALARM_TIMES, new UnsignedWordElement(0x219E)), //
						m(SingleRackVersionB.ChannelId.BATTERY_OVER_VOLTAGE_ALARM_TIMES,
								new UnsignedWordElement(0x219F)), //
						m(SingleRackVersionB.ChannelId.BATTERY_CHARGE_OVER_CURRENT_ALARM_TIMES,
								new UnsignedWordElement(0x21A0)), //
						m(SingleRackVersionB.ChannelId.CELL_VOLTAGE_LOW_ALARM_TIMES, new UnsignedWordElement(0x21A1)), //
						m(SingleRackVersionB.ChannelId.BATTERY_VOLTAGE_LOW_ALARM_TIMES,
								new UnsignedWordElement(0x21A2)), //
						m(SingleRackVersionB.ChannelId.BATTERY_DISCHARGE_OVER_CURRENT_ALARM_TIMES,
								new UnsignedWordElement(0x21A3)), //
						m(SingleRackVersionB.ChannelId.BATTERY_OVER_TEMPERATURE_ALARM_TIMES,
								new UnsignedWordElement(0x21A4)), //
						m(SingleRackVersionB.ChannelId.BATTERY_TEMPERATURE_LOW_ALARM_TIMES,
								new UnsignedWordElement(0x21A5)), //
						m(SingleRackVersionB.ChannelId.SYSTEM_SHORT_CIRCUIT_PROTECTION_TIMES,
								new UnsignedWordElement(0x21A6)), //
						m(SingleRackVersionB.ChannelId.SYSTEM_GR_OVER_TEMPERATURE_STOP_TIMES,
								new UnsignedWordElement(0x21A7)), //
						new DummyRegisterElement(0x21A8), //
						m(SingleRackVersionB.ChannelId.SYSTEM_GR_OVER_TEMPERATURE_ALARM_TIMES,
								new UnsignedWordElement(0x21A9)), //
						new DummyRegisterElement(0x21AA), //
						m(SingleRackVersionB.ChannelId.BATTERY_VOLTAGE_DIFFERENCE_ALARM_TIMES,
								new UnsignedWordElement(0x21AB)), //
						m(SingleRackVersionB.ChannelId.BATTERY_VOLTAGE_DIFFERENCE_STOP_TIMES,
								new UnsignedWordElement(0x21AC)), //
						new DummyRegisterElement(0x21AD, 0x21B3), //
						m(SingleRackVersionB.ChannelId.SLAVE_TEMPERATURE_COMMUNICATION_ERROR_HIGH,
								new UnsignedWordElement(0x21B4)), //
						m(SingleRackVersionB.ChannelId.SLAVE_TEMPERATURE_COMMUNICATION_ERROR_LOW,
								new UnsignedWordElement(0x21B5)) //
				), //

				// Add tasks to read/write work and warn parameters

				// Stop parameter
				new FC16WriteRegistersTask(0x2040, //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_VOLTAGE_PROTECTION,
								new UnsignedWordElement(0x2040)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2041)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_OVER_VOLTAGE_PROTECTION,
								new UnsignedWordElement(0x2042), ElementToChannelConverter.SCALE_FACTOR_2),
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_OVER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2043), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_PROTECTION,
								new UnsignedWordElement(0x2044), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_RECOVER,
								new UnsignedWordElement(0x2045), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_VOLTAGE_PROTECTION,
								new UnsignedWordElement(0x2046)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2047)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_UNDER_VOLTAGE_PROTECTION,
								new UnsignedWordElement(0x2048), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_UNDER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2049), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_PROTECTION,
								new UnsignedWordElement(0x204A), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_RECOVER,
								new UnsignedWordElement(0x204B), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_TEMPERATURE_PROTECTION,
								new SignedWordElement(0x204C)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_TEMPERATURE_RECOVER,
								new SignedWordElement(0x204D)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_TEMPERATURE_PROTECTION,
								new SignedWordElement(0x204E)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_TEMPERATURE_RECOVER,
								new SignedWordElement(0x204F)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_LOW_PROTECTION,
								new UnsignedWordElement(0x2050)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_LOW_PROTECTION_RECOVER,
								new UnsignedWordElement(0x2051)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_HIGH_PROTECTION,
								new UnsignedWordElement(0x2052)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_HIGH_PROTECTION_RECOVER,
								new UnsignedWordElement(0x2053)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_PROTECTION,
								new SignedWordElement(0x2054)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_PROTECTION_RECOVER,
								new SignedWordElement(0x2055)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_INSULATION_PROTECTION,
								new UnsignedWordElement(0x2056)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_INSULATION_PROTECTION_RECOVER,
								new UnsignedWordElement(0x2057)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_VOLTAGE_DIFFERENCE_PROTECTION,
								new UnsignedWordElement(0x2058)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_VOLTAGE_DIFFERENCE_PROTECTION_RECOVER,
								new UnsignedWordElement(0x2059)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_PROTECTION,
								new UnsignedWordElement(0x205A), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_PROTECTION_RECOVER,
								new UnsignedWordElement(0x205B), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_PROTECTION,
								new SignedWordElement(0x205C)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_PROTECTION_RECOVER,
								new SignedWordElement(0x205D)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_LOW_PROTECTION,
								new SignedWordElement(0x205E)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_LOW_PROTECTION_RECOVER,
								new SignedWordElement(0x205F)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TEMPERATURE_DIFFERENCE_PROTECTION,
								new SignedWordElement(0x2060)), //
						m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TEMPERATURE_DIFFERENCE_PROTECTION_RECOVER,
								new SignedWordElement(0x2061)) //
				),

				// Warn parameter
				new FC16WriteRegistersTask(0x2080, //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_VOLTAGE_ALARM,
								new UnsignedWordElement(0x2080)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2081)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_OVER_VOLTAGE_ALARM,
								new UnsignedWordElement(0x2082), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_OVER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2083), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_ALARM,
								new UnsignedWordElement(0x2084), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_RECOVER,
								new UnsignedWordElement(0x2085), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_VOLTAGE_ALARM,
								new UnsignedWordElement(0x2086)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2087)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_UNDER_VOLTAGE_ALARM,
								new UnsignedWordElement(0x2088), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_UNDER_VOLTAGE_RECOVER,
								new UnsignedWordElement(0x2089), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_ALARM,
								new UnsignedWordElement(0x208A), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_RECOVER,
								new UnsignedWordElement(0x208B), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_TEMPERATURE_ALARM,
								new SignedWordElement(0x208C)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_TEMPERATURE_RECOVER,
								new SignedWordElement(0x208D)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_TEMPERATURE_ALARM,
								new SignedWordElement(0x208E)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_TEMPERATURE_RECOVER,
								new SignedWordElement(0x208F)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_LOW_ALARM, new UnsignedWordElement(0x2090)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_LOW_ALARM_RECOVER,
								new UnsignedWordElement(0x2091)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_HIGH_ALARM, new UnsignedWordElement(0x2092)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_HIGH_ALARM_RECOVER,
								new UnsignedWordElement(0x2093)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_ALARM,
								new SignedWordElement(0x2094)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_ALARM_RECOVER,
								new SignedWordElement(0x2095)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_INSULATION_ALARM,
								new UnsignedWordElement(0x2096)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_INSULATION_ALARM_RECOVER,
								new UnsignedWordElement(0x2097)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_VOLTAGE_DIFFERENCE_ALARM,
								new UnsignedWordElement(0x2098)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_VOLTAGE_DIFFERENCE_ALARM_RECOVER,
								new UnsignedWordElement(0x2099)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_ALARM,
								new UnsignedWordElement(0x209A), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_ALARM_RECOVER,
								new UnsignedWordElement(0x209B), ElementToChannelConverter.SCALE_FACTOR_2), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_ALARM,
								new SignedWordElement(0x209C)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_ALARM_RECOVER,
								new SignedWordElement(0x209D)), //
						new DummyRegisterElement(0x209E),
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_LOW_ALARM,
								new SignedWordElement(0x209F)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_LOW_ALARM_RECOVER,
								new SignedWordElement(0x20A0)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TEMPERATURE_DIFFERENCE_ALARM,
								new SignedWordElement(0x20A1)), //
						m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TEMPERATURE_DIFFERENCE_ALARM_RECOVER,
								new SignedWordElement(0x20A2)) //
				),

				new FC6WriteRegisterTask(0x20DF,
						m(SingleRackVersionB.ChannelId.SET_SOC, new UnsignedWordElement(0x20DF)))

		);

		if (!this.config.ReduceTasks()) {
			// Stop parameter
			Task readStopParameters = new FC3ReadRegistersTask(0x2040, Priority.LOW, //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_VOLTAGE_PROTECTION,
							new UnsignedWordElement(0x2040)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2041)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_OVER_VOLTAGE_PROTECTION,
							new UnsignedWordElement(0x2042), ElementToChannelConverter.SCALE_FACTOR_2),
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_OVER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2043), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_PROTECTION,
							new UnsignedWordElement(0x2044), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_RECOVER,
							new UnsignedWordElement(0x2045), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_VOLTAGE_PROTECTION,
							new UnsignedWordElement(0x2046)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2047)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_UNDER_VOLTAGE_PROTECTION,
							new UnsignedWordElement(0x2048), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_UNDER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2049), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_PROTECTION,
							new UnsignedWordElement(0x204A), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_RECOVER,
							new UnsignedWordElement(0x204B), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_TEMPERATURE_PROTECTION,
							new SignedWordElement(0x204C)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_OVER_TEMPERATURE_RECOVER,
							new SignedWordElement(0x204D)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_TEMPERATURE_PROTECTION,
							new SignedWordElement(0x204E)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_UNDER_TEMPERATURE_RECOVER,
							new SignedWordElement(0x204F)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_LOW_PROTECTION, new UnsignedWordElement(0x2050)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_LOW_PROTECTION_RECOVER,
							new UnsignedWordElement(0x2051)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_HIGH_PROTECTION, new UnsignedWordElement(0x2052)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_SOC_HIGH_PROTECTION_RECOVER,
							new UnsignedWordElement(0x2053)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_PROTECTION,
							new SignedWordElement(0x2054)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_PROTECTION_RECOVER,
							new SignedWordElement(0x2055)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_INSULATION_PROTECTION,
							new UnsignedWordElement(0x2056)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_INSULATION_PROTECTION_RECOVER,
							new UnsignedWordElement(0x2057)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_VOLTAGE_DIFFERENCE_PROTECTION,
							new UnsignedWordElement(0x2058)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_CELL_VOLTAGE_DIFFERENCE_PROTECTION_RECOVER,
							new UnsignedWordElement(0x2059)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_PROTECTION,
							new UnsignedWordElement(0x205A), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_PROTECTION_RECOVER,
							new UnsignedWordElement(0x205B), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_PROTECTION,
							new SignedWordElement(0x205C)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_PROTECTION_RECOVER,
							new SignedWordElement(0x205D)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_LOW_PROTECTION,
							new SignedWordElement(0x205E)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_DISCHARGE_TEMPERATURE_LOW_PROTECTION_RECOVER,
							new SignedWordElement(0x205F)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TEMPERATURE_DIFFERENCE_PROTECTION,
							new SignedWordElement(0x2060)), //
					m(SingleRackVersionB.ChannelId.STOP_PARAMETER_TEMPERATURE_DIFFERENCE_PROTECTION_RECOVER,
							new SignedWordElement(0x2061)) //
			);

			// Warn parameter
			Task readWarnParameters = new FC3ReadRegistersTask(0x2080, Priority.LOW, //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_VOLTAGE_ALARM,
							new UnsignedWordElement(0x2080)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2081)), //
					new DummyRegisterElement(0x2082),
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_OVER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2083), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_ALARM,
							new UnsignedWordElement(0x2084), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_CHARGE_OVER_CURRENT_RECOVER,
							new UnsignedWordElement(0x2085), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_VOLTAGE_ALARM,
							new UnsignedWordElement(0x2086)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2087)), //
					new DummyRegisterElement(0x2088),
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_UNDER_VOLTAGE_RECOVER,
							new UnsignedWordElement(0x2089), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_ALARM,
							new UnsignedWordElement(0x208A), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SYSTEM_DISCHARGE_OVER_CURRENT_RECOVER,
							new UnsignedWordElement(0x208B), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_TEMPERATURE_ALARM,
							new SignedWordElement(0x208C)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_OVER_TEMPERATURE_RECOVER,
							new SignedWordElement(0x208D)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_TEMPERATURE_ALARM,
							new SignedWordElement(0x208E)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_UNDER_TEMPERATURE_RECOVER,
							new SignedWordElement(0x208F)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_LOW_ALARM, new UnsignedWordElement(0x2090)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_LOW_ALARM_RECOVER,
							new UnsignedWordElement(0x2091)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_HIGH_ALARM, new UnsignedWordElement(0x2092)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_SOC_HIGH_ALARM_RECOVER,
							new UnsignedWordElement(0x2093)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_ALARM,
							new SignedWordElement(0x2094)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CONNECTOR_TEMPERATURE_HIGH_ALARM_RECOVER,
							new SignedWordElement(0x2095)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_INSULATION_ALARM, new UnsignedWordElement(0x2096)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_INSULATION_ALARM_RECOVER,
							new UnsignedWordElement(0x2097)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_VOLTAGE_DIFFERENCE_ALARM,
							new UnsignedWordElement(0x2098)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_CELL_VOLTAGE_DIFFERENCE_ALARM_RECOVER,
							new UnsignedWordElement(0x2099)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_ALARM,
							new UnsignedWordElement(0x209A), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TOTAL_VOLTAGE_DIFFERENCE_ALARM_RECOVER,
							new UnsignedWordElement(0x209B), ElementToChannelConverter.SCALE_FACTOR_2), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_ALARM,
							new SignedWordElement(0x209C)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_HIGH_ALARM_RECOVER,
							new SignedWordElement(0x209D)), //
					new DummyRegisterElement(0x209E),
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_LOW_ALARM,
							new SignedWordElement(0x209F)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_DISCHARGE_TEMPERATURE_LOW_ALARM_RECOVER,
							new SignedWordElement(0x20A0)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TEMPERATURE_DIFFERENCE_ALARM,
							new SignedWordElement(0x20A1)), //
					m(SingleRackVersionB.ChannelId.WARN_PARAMETER_TEMPERATURE_DIFFERENCE_ALARM_RECOVER,
							new SignedWordElement(0x20A2)) //
			);

			protocol.addTask(readStopParameters);
			protocol.addTask(readWarnParameters);
		}

		return protocol;
	}

	/**
	 * Gets the Number of Modules.
	 * 
	 * @return the Number of Modules as a {@link CompletableFuture}.
	 * @throws OpenemsException on error
	 */
	private CompletableFuture<Integer> getNumberOfModules() {
		final CompletableFuture<Integer> result = new CompletableFuture<Integer>();
		try {
			ModbusUtils.readELementOnce(this.getModbusProtocol(), new UnsignedWordElement(0x20C1), true)
					.thenAccept(numberOfModules -> {
						if (numberOfModules == null) {
							return;
						}
						result.complete(numberOfModules);
					});
		} catch (OpenemsException e) {
			result.completeExceptionally(e);
		}
		return result;
	}

	/**
	 * Calculates the Capacity as Capacity per module multiplied with number of
	 * modules and sets the CAPACITY channel.
	 * 
	 * @param numberOfModules the number of battery modules
	 */
	private void calculateCapacity(Integer numberOfModules) {
		int capacity = numberOfModules * this.config.moduleType().getCapacity_Wh();
		this._setCapacity(capacity);
	}

	private static final int ADDRESS_OFFSET = 0x2000;
	public static final int VOLTAGE_ADDRESS_OFFSET = ADDRESS_OFFSET + 0x800;
	public static final int TEMPERATURE_ADDRESS_OFFSET = ADDRESS_OFFSET + 0xC00;
	public static final int SENSORS_PER_MODULE = 12;

	/*
	 * Dynamically generate Channels and Modbus mappings for Cell-Temperatures and
	 * for Cell-Voltages. Channel-IDs are like "CLUSTER_1_BATTERY_001_VOLTAGE".
	 * 
	 * @param numberOfModules the number of battery modules
	 */
	private void createDynamicChannels(int numberOfModules) {
		try {
			for (int i = 0; i < numberOfModules; i++) {
				AbstractModbusElement<?>[] ameVolt = new AbstractModbusElement<?>[SENSORS_PER_MODULE];
				AbstractModbusElement<?>[] ameTemp = new AbstractModbusElement<?>[SENSORS_PER_MODULE];
				for (int j = 0; j < SENSORS_PER_MODULE; j++) {
					int sensor = i * SENSORS_PER_MODULE + j;
					{
						// Create Voltage Channel
						ChannelIdImpl channelId = new ChannelIdImpl(
								"CLUSTER_1_BATTERY_" + String.format("%03d", sensor) + "_VOLTAGE",
								Doc.of(OpenemsType.INTEGER).unit(Unit.MILLIVOLT));
						this.addChannel(channelId);
						// Create Modbus-Mapping for Voltages
						UnsignedWordElement uwe = new UnsignedWordElement(VOLTAGE_ADDRESS_OFFSET + sensor);
						ameVolt[j] = m(channelId, uwe);
					}
					{
						// Create Temperature Channel
						ChannelIdImpl channelId = new ChannelIdImpl(
								"CLUSTER_1_BATTERY_" + String.format("%03d", sensor) + "_TEMPERATURE",
								Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS));
						this.addChannel(channelId);
						// Create Modbus-Mapping for Temperatures
						UnsignedWordElement uwe = new UnsignedWordElement(TEMPERATURE_ADDRESS_OFFSET + sensor);
						ameTemp[j] = m(channelId, uwe);
					}
				}
				this.getModbusProtocol().addTasks(//
						new FC3ReadRegistersTask(VOLTAGE_ADDRESS_OFFSET + i * SENSORS_PER_MODULE, Priority.LOW,
								ameVolt), //
						new FC3ReadRegistersTask(TEMPERATURE_ADDRESS_OFFSET + i * SENSORS_PER_MODULE, Priority.LOW,
								ameTemp));
			}
		} catch (OpenemsException e) {
			e.printStackTrace();
		}
	}
}

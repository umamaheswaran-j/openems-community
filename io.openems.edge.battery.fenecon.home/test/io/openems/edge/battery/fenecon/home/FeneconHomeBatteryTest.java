package io.openems.edge.battery.fenecon.home;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.fenecon.home.enums.BmsControl;
import io.openems.edge.battery.fenecon.home.statemachine.StateMachine;
import io.openems.edge.battery.protection.BatteryProtection;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.io.test.DummyInputOutput;

public class FeneconHomeBatteryTest {

	private static final String BATTERY_ID = "battery0";
	private static final String MODBUS_ID = "modbus0";
	private static final String IO_ID = "io0";

	private static final ChannelAddress STATE_MACHINE = new ChannelAddress(BATTERY_ID,
			FeneconHomeBattery.ChannelId.STATE_MACHINE.id());
	private static final ChannelAddress BMS_CONTROL = new ChannelAddress(BATTERY_ID,
			FeneconHomeBattery.ChannelId.BMS_CONTROL.id());
	private static final ChannelAddress BP_CHARGE_BMS = new ChannelAddress(BATTERY_ID,
			BatteryProtection.ChannelId.BP_CHARGE_BMS.id());
	private static final ChannelAddress MAX_CELL_VOLTAGE = new ChannelAddress(BATTERY_ID,
			Battery.ChannelId.MAX_CELL_VOLTAGE.id());
	private static final ChannelAddress CHARGE_MAX_CURRENT = new ChannelAddress(BATTERY_ID,
			Battery.ChannelId.CHARGE_MAX_CURRENT.id());
	private static final ChannelAddress BATTERY_RELAY = new ChannelAddress(IO_ID, "InputOutput4");

	/**
	 * Battery start up when the relay and battery off test.
	 * 
	 * @throws Exception on error
	 */
	@Test
	public void test() throws Exception {
		final TimeLeapClock clock = new TimeLeapClock(Instant.parse("2020-01-01T01:00:00.00Z"), ZoneOffset.UTC);

		new ComponentTest(new FeneconHomeBatteryImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)) //
				.addComponent(new DummyInputOutput(IO_ID))//
				.activate(MyConfig.create() //
						.setId(BATTERY_ID) //
						.setModbusId(MODBUS_ID) //
						.setModbusUnitId(0) //
						.setStartStop(StartStopConfig.START) //
						.setBatteryStartUpRelay("io0/InputOutput4")//
						.build())//

				.next(new TestCase("Battery Relay false") //
						.input(BATTERY_RELAY, false)//
						.input(BMS_CONTROL, BmsControl.SWITCHED_OFF)//
						.output(STATE_MACHINE, StateMachine.State.UNDEFINED))//
				.next(new TestCase() //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.input(BATTERY_RELAY, true))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL") //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.input(BMS_CONTROL, BmsControl.SWITCHED_ON) //
						.output(BATTERY_RELAY, false)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_OFF") //
						.input(BATTERY_RELAY, false) //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("FINISHED")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase()//
						.output(STATE_MACHINE, StateMachine.State.RUNNING))//

				// Ramp-Up ChargeMaxCurrent (0.1 A / Second)
				.next(new TestCase() //
						.input(BP_CHARGE_BMS, 40) //
						.input(MAX_CELL_VOLTAGE, 3000)) //
				.next(new TestCase("Ramp up") //
						.timeleap(clock, 100, ChronoUnit.SECONDS) //
						.output(CHARGE_MAX_CURRENT, 10)) //
				.next(new TestCase() //
						.timeleap(clock, 300, ChronoUnit.SECONDS) //
						.output(CHARGE_MAX_CURRENT, 40))

				// Full Battery
				.next(new TestCase() //
						.input(BP_CHARGE_BMS, 15) //
						.input(MAX_CELL_VOLTAGE, 3400)) //
				.next(new TestCase() //
						.output(CHARGE_MAX_CURRENT, 15)) //
				.next(new TestCase() //
						.input(BP_CHARGE_BMS, 40)) //
				.next(new TestCase() //
						.timeleap(clock, 100, ChronoUnit.SECONDS) //
						.output(CHARGE_MAX_CURRENT, 25)) //
		;
	}

	/**
	 * Battery start up when the relay is on and battery is off test.
	 * 
	 * @throws Exception on error
	 */
	@Test
	public void test2() throws Exception {
		new ComponentTest(new FeneconHomeBatteryImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)) //
				.addComponent(new DummyInputOutput(IO_ID))//
				.activate(MyConfig.create() //
						.setId(BATTERY_ID) //
						.setModbusId(MODBUS_ID) //
						.setModbusUnitId(0) //
						.setStartStop(StartStopConfig.START) //
						.setBatteryStartUpRelay("io0/InputOutput4")//
						.build())//

				.next(new TestCase()//
						.input(BATTERY_RELAY, true)//
						.input(BMS_CONTROL, BmsControl.SWITCHED_OFF)//
						.output(STATE_MACHINE, StateMachine.State.UNDEFINED))//
				.next(new TestCase() //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL") //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.input(BMS_CONTROL, BmsControl.SWITCHED_ON)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_OFF") //
						.input(BATTERY_RELAY, false) //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("FINISHED")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase()//
						.output(STATE_MACHINE, StateMachine.State.RUNNING));
	}

	/**
	 * Battery start up when the relay is off and battery has already started, FEMS
	 * restarted.
	 * 
	 * @throws Exception on error
	 */
	@Test
	public void test3() throws Exception {
		new ComponentTest(new FeneconHomeBatteryImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)) //
				.addComponent(new DummyInputOutput(IO_ID))//
				.activate(MyConfig.create() //
						.setId(BATTERY_ID) //
						.setModbusId(MODBUS_ID) //
						.setModbusUnitId(0) //
						.setStartStop(StartStopConfig.START) //
						.setBatteryStartUpRelay("io0/InputOutput4")//
						.build())//

				.next(new TestCase()//
						.input(BATTERY_RELAY, false)//
						.input(BMS_CONTROL, BmsControl.SWITCHED_ON)//
						.output(STATE_MACHINE, StateMachine.State.UNDEFINED))//
				.next(new TestCase() //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(BATTERY_RELAY, false)//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL") //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_OFF") //
						.output(STATE_MACHINE, StateMachine.State.RUNNING)) //
				.next(new TestCase("FINISHED")//
						.output(STATE_MACHINE, StateMachine.State.RUNNING));
	}

	/**
	 * Battery hard switch is off, should stay in GO_RUNNING.
	 * 
	 * @throws Exception on error
	 */
	@Test
	public void test4() throws Exception {
		new ComponentTest(new FeneconHomeBatteryImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)) //
				.addComponent(new DummyInputOutput(IO_ID))//
				.activate(MyConfig.create() //
						.setId(BATTERY_ID) //
						.setModbusId(MODBUS_ID) //
						.setModbusUnitId(0) //
						.setStartStop(StartStopConfig.START) //
						.setBatteryStartUpRelay("io0/InputOutput4")//
						.build())//

				.next(new TestCase()//
						.input(BATTERY_RELAY, false)//
						.input(BMS_CONTROL, BmsControl.SWITCHED_OFF)//
						.output(STATE_MACHINE, StateMachine.State.UNDEFINED))//
				.next(new TestCase() //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL") //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				// Ex; after long time if hard switch turned on....
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL")//
						.input(BMS_CONTROL, BmsControl.SWITCHED_ON)//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_OFF") //
						.input(BATTERY_RELAY, false) //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("FINISHED")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase()//
						.output(STATE_MACHINE, StateMachine.State.RUNNING));
	}

	/**
	 * Configuration problems, IO wrong configured channel, if battery has not
	 * started yet, in these case Fault state should be taken care(TODO).
	 * 
	 * @throws Exception on error
	 */
	@Test
	public void test5() throws Exception {
		new ComponentTest(new FeneconHomeBatteryImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)) //
				.addComponent(new DummyInputOutput(IO_ID))//
				.activate(MyConfig.create() //
						.setId(BATTERY_ID) //
						.setModbusId(MODBUS_ID) //
						.setModbusUnitId(0) //
						.setStartStop(StartStopConfig.START) //
						.setBatteryStartUpRelay("io1/InputOutput4")//
						.build())//

				.next(new TestCase("Battery Relay false") //
						.input(BATTERY_RELAY, false)//
						.input(BMS_CONTROL, BmsControl.SWITCHED_OFF)//
						.output(STATE_MACHINE, StateMachine.State.UNDEFINED))//
				.next(new TestCase() //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING));//

	}

	/**
	 * Configuration problems, or Relay board is not connected, if battery already
	 * started.
	 * 
	 * @throws Exception on error
	 */
	@Test
	public void test6() throws Exception {
		new ComponentTest(new FeneconHomeBatteryImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("setModbus", new DummyModbusBridge(MODBUS_ID)) //
				.addComponent(new DummyInputOutput(IO_ID))//
				.activate(MyConfig.create() //
						.setId(BATTERY_ID) //
						.setModbusId(MODBUS_ID) //
						.setModbusUnitId(0) //
						.setStartStop(StartStopConfig.START) //
						.setBatteryStartUpRelay("io1/InputOutput4")//
						.build())//

				.next(new TestCase()//
						.input(BATTERY_RELAY, false)//
						.input(BMS_CONTROL, BmsControl.SWITCHED_ON)//
						.output(STATE_MACHINE, StateMachine.State.UNDEFINED))//
				.next(new TestCase() //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING)) //
				.next(new TestCase("in WAIT_FOR_SWITCH_ON")//
						.output(BATTERY_RELAY, false)//
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_BMS_CONTROL") //
						.output(STATE_MACHINE, StateMachine.State.GO_RUNNING))//
				.next(new TestCase("in WAIT_FOR_SWITCH_OFF")//
						.output(STATE_MACHINE, StateMachine.State.RUNNING))//
				.next(new TestCase("FINISHED")//
						.output(STATE_MACHINE, StateMachine.State.RUNNING));
	}

}

package io.openems.edge.ess.generic.symmetric;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.battery.test.DummyBattery;
import io.openems.edge.batteryinverter.test.DummyManagedSymmetricBatteryInverter;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.ess.generic.common.GenericManagedEss;
import io.openems.edge.ess.generic.symmetric.statemachine.StateMachine.State;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.ess.test.ManagedSymmetricEssTest;

public class GenericManagedSymmetricEssTest {

	private static final String ESS_ID = "ess0";
	private static final String BATTERY_ID = "battery0";
	private static final String BATTERY_INVERTER_ID = "batteryInverter0";

	private static final ChannelAddress ESS_STATE_MACHINE = new ChannelAddress(ESS_ID, "StateMachine");
	private static final ChannelAddress ESS_ALLOWED_DISCHARGE_POWER = new ChannelAddress(ESS_ID,
			"AllowedDischargePower");

	private static final ChannelAddress BATTERY_START_STOP = new ChannelAddress(BATTERY_ID, "StartStop");
	private static final ChannelAddress BATTERY_CHARGE_MAX_CURRENT = new ChannelAddress(BATTERY_ID, "ChargeMaxCurrent");
	private static final ChannelAddress BATTERY_DISCHARGE_MAX_CURRENT = new ChannelAddress(BATTERY_ID,
			"DischargeMaxCurrent");

	private static final ChannelAddress BATTERY_INVERTER_START_STOP = new ChannelAddress(BATTERY_INVERTER_ID,
			"StartStop");
	private static final ChannelAddress BATTERY_INVERTER_ACTIVE_POWER = new ChannelAddress(BATTERY_INVERTER_ID,
			"ActivePower");

	@Test
	public void testStart() throws Exception {
		final TimeLeapClock clock = new TimeLeapClock(Instant.parse("2020-01-01T01:00:00.00Z"), ZoneOffset.UTC);
		new ComponentTest(new GenericManagedSymmetricEssImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("batteryInverter", new DummyManagedSymmetricBatteryInverter(BATTERY_INVERTER_ID)) //
				.addReference("battery", new DummyBattery(BATTERY_ID)) //
				.activate(MyConfig.create() //
						.setId(ESS_ID) //
						.setStartStopConfig(StartStopConfig.START) //
						.setBatteryInverterId(BATTERY_INVERTER_ID) //
						.setBatteryId(BATTERY_ID) //
						.build()) //
				.next(new TestCase() //
						.output(ESS_STATE_MACHINE, State.UNDEFINED)) //
				.next(new TestCase() //
						.output(ESS_STATE_MACHINE, State.START_BATTERY)) //
				.next(new TestCase("Start the Battery") //
						.input(BATTERY_START_STOP, StartStop.START)) //
				.next(new TestCase() //
						.output(ESS_STATE_MACHINE, State.START_BATTERY_INVERTER)) //
				.next(new TestCase("Start the Battery-Inverter") //
						.input(BATTERY_INVERTER_START_STOP, StartStop.START)) //
				.next(new TestCase() //
						.output(ESS_STATE_MACHINE, State.STARTED)) //
		;
	}

	@Test
	public void testForceCharge() throws Exception {
		new ManagedSymmetricEssTest(new GenericManagedSymmetricEssImpl()) //
				.addReference("power", new DummyPower()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("batteryInverter", new DummyManagedSymmetricBatteryInverter(BATTERY_INVERTER_ID)) //
				.addReference("battery", new DummyBattery(BATTERY_ID) //
						.withVoltage(500) //
						.withChargeMaxCurrent(50) //
						.withDischargeMaxCurrent(-5) //
				) //
				.activate(MyConfig.create() //
						.setId(ESS_ID) //
						.setStartStopConfig(StartStopConfig.START) //
						.setBatteryInverterId(BATTERY_INVERTER_ID) //
						.setBatteryId(BATTERY_ID) //
						.build()) //
				.next(new TestCase("Start the Battery") //
						.input(BATTERY_START_STOP, StartStop.START) //
						.output(ESS_ALLOWED_DISCHARGE_POWER, 0) //
						.output(BATTERY_INVERTER_ACTIVE_POWER, 0)) //
				.next(new TestCase()) //
				.next(new TestCase("Start the Battery-Inverter") //
						.input(BATTERY_INVERTER_START_STOP, StartStop.START)) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.input(BATTERY_CHARGE_MAX_CURRENT, 50) //
						.input(BATTERY_DISCHARGE_MAX_CURRENT, -5) //
						.output(ESS_ALLOWED_DISCHARGE_POWER, (int) (-2500 * GenericManagedEss.EFFICIENCY_FACTOR))) //
		;
	}

}

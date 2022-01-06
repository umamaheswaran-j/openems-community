package io.openems.edge.controller.api.mqtt;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

import io.openems.common.channel.PersistencePriority;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.TimeLeapClock;

public class MqttApiControllerImplTest {

	private static final String CTRL_ID = "ctrl0";

	@Test
	public void test() throws Exception {
		final TimeLeapClock clock = new TimeLeapClock(
				Instant.ofEpochSecond(1577836800L) /* starts at 1. January 2020 00:00:00 */, ZoneOffset.UTC);
		new ComponentTest(new MqttApiControllerImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummySum()) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setClientId("edge0") //
						.setUsername("guest") //
						.setPassword("guest") //
						.setUri("ws://localhost:1883") //
						.setPersistencePriority(PersistencePriority.VERY_LOW) //
						.setDebugMode(true) //
						.build());
	}

}

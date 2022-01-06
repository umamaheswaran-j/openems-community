package io.openems.edge.bosch.bpts5hybrid.meter;

import org.junit.Test;

import io.openems.edge.bosch.bpts5hybrid.core.BoschBpts5HybridCoreImpl;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;

public class BoschBpts5HybridMeterTest {

	private static final String CORE_ID = "core0";
	private static final String METER_ID = "meter0";

	@Test
	public void test() throws Exception {
		BoschBpts5HybridCoreImpl core = new BoschBpts5HybridCoreImpl();
		new ComponentTest(new BoschBpts5HybridCoreImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.activate(io.openems.edge.bosch.bpts5hybrid.core.MyConfig.create() //
						.setId(CORE_ID) //
						.setIpaddress("127.0.0.1") //
						.setInterval(2) //
						.build()); //

		new ComponentTest(new BoschBpts5HybridMeter()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("core", core) //
				.activate(MyConfig.create() //
						.setId(METER_ID) //
						.setCoreId(CORE_ID) //
						.build()) //
		;
	}
}
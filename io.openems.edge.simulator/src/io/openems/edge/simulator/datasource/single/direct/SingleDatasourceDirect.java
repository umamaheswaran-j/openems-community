package io.openems.edge.simulator.datasource.single.direct;

import java.io.IOException;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.simulator.DataContainer;
import io.openems.edge.simulator.datasource.api.AbstractCsvDatasource;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Simulator.Datasource.Single.Direct", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE)
public class SingleDatasourceDirect extends AbstractCsvDatasource
		implements SimulatorDatasource, OpenemsComponent, EventHandler {

	@Reference
	private ComponentManager componentManager;

	private Config config;

	public SingleDatasourceDirect() {
		super(//
				OpenemsComponent.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) throws NumberFormatException, IOException {
		this.config = config;
		super.activate(context, config.id(), config.alias(), config.enabled(), config.timeDelta());
	}

	@Override
	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	@Override
	protected DataContainer getData() throws NumberFormatException, IOException {
		DataContainer result = new DataContainer();
		for (int value : this.config.values()) {
			result.addRecord(new Float[] { Float.valueOf(value) });
		}
		return result;
	}

}

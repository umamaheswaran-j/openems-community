package io.openems.edge.scheduler.allalphabetically;

import java.util.LinkedHashSet;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.scheduler.api.Scheduler;

/**
 * This Scheduler returns all existing Controllers ordered by their ID.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Scheduler.AllAlphabetically", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class AllAlphabeticallySchedulerImpl extends AbstractOpenemsComponent
		implements AllAlphabeticallyScheduler, Scheduler, OpenemsComponent {

	@Reference
	protected ComponentManager componentManager;

	private Config config;

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	public AllAlphabeticallySchedulerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Scheduler.ChannelId.values(), //
				AllAlphabeticallyScheduler.ChannelId.values() //
		);
	}

	@Override
	public LinkedHashSet<String> getControllers() {
		LinkedHashSet<String> result = new LinkedHashSet<>();

		// add sorted controllers
		for (String id : this.config.controllers_ids()) {
			if (id.equals("")) {
				continue;
			}
			result.add(id);
		}

		// add remaining controllers
		this.componentManager.getEnabledComponents().stream() //
				.filter(c -> c instanceof Controller) //
				.sorted((c1, c2) -> c1.id().compareTo(c2.id())) //
				.forEach(c -> result.add(c.id()));

		return result;
	}
}

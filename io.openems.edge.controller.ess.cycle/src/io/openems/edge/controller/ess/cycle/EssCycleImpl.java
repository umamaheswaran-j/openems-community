package io.openems.edge.controller.ess.cycle;

import java.time.LocalDateTime;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.cycle.statemachine.Context;
import io.openems.edge.controller.ess.cycle.statemachine.StateMachine;
import io.openems.edge.controller.ess.cycle.statemachine.StateMachine.State;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.ess.power.api.Pwr;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.Cycle", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EssCycleImpl extends AbstractOpenemsComponent implements Controller, OpenemsComponent, EssCycle {

	private final Logger log = LoggerFactory.getLogger(EssCycleImpl.class);

	// Time formatter from the String
	public static final String TIME_FORMAT = "HH:mm";

	private StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private Config config;

	/**
	 * Timestamp of the last time the State has changed.
	 */
	private LocalDateTime lastStateChange = LocalDateTime.MIN;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private Power power;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private ManagedSymmetricEss ess;

	public EssCycleImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				EssCycle.ChannelId.values() //
		);
		this._setCompletedCycles(0);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		this.config = config;
		super.activate(context, this.config.id(), this.config.alias(), this.config.enabled());
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "ess", this.config.ess_id())) {
			return;
		}
	}

	// TODO add @Modified to enable configuration changes during long running cycles

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {

		// get max charge/discharge power
		int maxDischargePower = this.ess.getPower().getMaxPower(this.ess, Phase.ALL, Pwr.ACTIVE);
		int maxChargePower = this.ess.getPower().getMinPower(this.ess, Phase.ALL, Pwr.ACTIVE);

		// store current state in StateMachine channel
		State state = this.stateMachine.getCurrentState();
		this.channel(EssCycle.ChannelId.STATE_MACHINE).setNextValue(state);

		// Prepare Context
		State previousState = this.stateMachine.getPreviousState();
		Context context = new Context(this, this.config, this.componentManager, this.ess, maxChargePower,
				maxDischargePower, previousState, this.lastStateChange);

		// Call the StateMachine
		try {
			this.stateMachine.run(context);
		} catch (OpenemsNamedException e) {
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}

		if (previousState != state) {
			// State has changed
			this.lastStateChange = LocalDateTime.now(this.componentManager.getClock());
		}
	}

}

package io.openems.edge.controller.ess.emergencycapacityreserve;

import java.util.OptionalInt;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.filter.RampFilter;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.emergencycapacityreserve.statemachine.Context;
import io.openems.edge.controller.ess.emergencycapacityreserve.statemachine.StateMachine;
import io.openems.edge.controller.ess.emergencycapacityreserve.statemachine.StateMachine.State;
import io.openems.edge.ess.api.ManagedSymmetricEss;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.EmergencyCapacityReserve", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EmergencyCapacityReserveImpl extends AbstractOpenemsComponent
		implements EmergencyCapacityReserve, Controller, OpenemsComponent {

	@Reference
	protected ComponentManager componentManager;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private Sum sum;

	@Reference
	private ManagedSymmetricEss ess;

	private Config config;

	/**
	 * Minimum reserve SoC value in [%]
	 */
	private static final int reservSocMinValue = 5;

	/**
	 * Maximum reserve SoC value in [%]
	 */
	private static final int reservSocMaxValue = 100;

	private final StateMachine stateMachine = new StateMachine(State.NO_LIMIT);

	private final RampFilter rampFilter = new RampFilter();

	private final Logger log = LoggerFactory.getLogger(EmergencyCapacityReserveImpl.class);

	public EmergencyCapacityReserveImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				EmergencyCapacityReserve.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.updateConfig(config);
	}

	@Override
	protected void modified(ComponentContext context, String id, String alias, boolean enabled) {
		super.modified(context, id, alias, enabled);
		this.updateConfig(config);
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		Context context = this.handleStateMachine();

		if (this.config.isReserveSocEnabled()) {
			Integer activePowerConstraint = rampFilter.getFilteredValueAsInteger(context.getTargetPower(),
					context.getRampPower());

			if (activePowerConstraint != null && context.maxApparentPower > activePowerConstraint) {
				// Set constraint did not reach max apparent power
				ess.setActivePowerLessOrEquals(activePowerConstraint);
				this._setDebugSetActivePowerLessOrEquals(activePowerConstraint);
			} else {
				// Set no constraint max apparent power reached
				ess.setActivePowerLessOrEquals(null);
				this._setDebugSetActivePowerLessOrEquals(null);
			}

			// set debug channels
			this._setDebugTargetPower(context.getTargetPower());
			this._setDebugRampPower(context.getRampPower());
		}

	}

	/**
	 * Update {@link Config} for the controller.
	 * 
	 * @param config to update
	 */
	private void updateConfig(Config config) {
		this.config = config;

		boolean enableWarning = false;
		if (this.config.reserveSoc() < reservSocMinValue || this.config.reserveSoc() > reservSocMaxValue) {
			enableWarning = true;
		}

		this._setRangeOfReserveSocOutsideAllowedValue(enableWarning);

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "ess", config.ess_id())) {
			return;
		}
	}

	/**
	 * Handle different {@link State} of the {@link StateMachine}.
	 * 
	 * @return created {@link Context}
	 */
	private Context handleStateMachine() {
		this._setStateMachine(this.stateMachine.getCurrentState());

		Value<Integer> soc = ess.getSoc();
		Value<Integer> maxApparentPower = ess.getMaxApparentPower();

		Integer socToUse = null;
		if (!soc.isDefined()) {
			// use last valid soc value
			OptionalInt lastSocValue = this.getLastValidSoc(ess.getSocChannel());
			if (lastSocValue.isPresent()) {
				socToUse = lastSocValue.getAsInt();
			}
		} else {
			// use current soc value
			socToUse = soc.get();
		}

		if (socToUse == null || !maxApparentPower.isDefined()) {
			this.stateMachine.forceNextState(State.NO_LIMIT);
		}

		Context context = new Context(this, this.sum, maxApparentPower.get(), socToUse, this.config.reserveSoc());
		try {
			this.stateMachine.run(context);

			this.channel(Controller.ChannelId.RUN_FAILED).setNextValue(false);

		} catch (OpenemsNamedException e) {
			this.channel(Controller.ChannelId.RUN_FAILED).setNextValue(true);
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}

		return context;
	}

	/**
	 * Get last defined value of an {@link IntegerReadChannel} as an
	 * {@link OptionalInt}.
	 * 
	 * @param channel {@link IntegerReadChannel} to get values
	 * @return Last defined value from given {@link IntegerReadChannel}
	 */
	private OptionalInt getLastValidSoc(IntegerReadChannel channel) {
		// get first defined value
		return channel.getPastValues().values() //
				.stream() //
				.filter(value -> value.isDefined()) //
				.mapToInt(value -> value.get()) //
				.findFirst();
	}

	@Override
	public Config getConfig() {
		return this.config;
	}

}

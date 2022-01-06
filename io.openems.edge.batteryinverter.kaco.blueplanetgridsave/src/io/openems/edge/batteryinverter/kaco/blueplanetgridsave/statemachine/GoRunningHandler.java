package io.openems.edge.batteryinverter.kaco.blueplanetgridsave.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.batteryinverter.kaco.blueplanetgridsave.KacoBlueplanetGridsave;
import io.openems.edge.batteryinverter.kaco.blueplanetgridsave.KacoSunSpecModel.S64201.S64201RequestedState;
import io.openems.edge.batteryinverter.kaco.blueplanetgridsave.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class GoRunningHandler extends StateHandler<State, Context> {

	private Instant lastAttempt = Instant.MIN;
	private int attemptCounter = 0;

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.lastAttempt = Instant.MIN;
		this.attemptCounter = 0;
		KacoBlueplanetGridsave inverter = context.getParent();
		inverter._setMaxStartAttempts(false);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		KacoBlueplanetGridsave inverter = context.getParent();

		// Has Faults -> abort
		if (inverter.hasFaults()) {
			return State.UNDEFINED;
		}

		switch (inverter.getCurrentState()) {
		case GRID_CONNECTED:
			// All Good

		case THROTTLED:
			// if inverter is throttled, full power is not available, but the device
			// is still working
			return State.RUNNING;

		case FAULT:
		case GRID_PRE_CONNECTED:
		case MPPT:
		case NO_ERROR_PENDING:
		case OFF:
		case PRECHARGE:
		case SHUTTING_DOWN:
		case SLEEPING:
		case STANDBY:
		case STARTING:
		case UNDEFINED:
			// Not yet running...
		}

		boolean isMaxStartTimePassed = Duration.between(this.lastAttempt, Instant.now())
				.getSeconds() > KacoBlueplanetGridsave.RETRY_COMMAND_SECONDS;
		if (isMaxStartTimePassed) {
			// First try - or waited long enough for next try

			if (this.attemptCounter > KacoBlueplanetGridsave.RETRY_COMMAND_MAX_ATTEMPTS) {
				// Too many tries
				inverter._setMaxStartAttempts(true);
				return State.UNDEFINED;

			} else {
				// Trying to switch on
				inverter.setRequestedState(S64201RequestedState.GRID_CONNECTED);
				this.lastAttempt = Instant.now();
				this.attemptCounter++;
				return State.GO_RUNNING;

			}

		} else {
			// Still waiting...
			return State.GO_RUNNING;
		}
	}

}

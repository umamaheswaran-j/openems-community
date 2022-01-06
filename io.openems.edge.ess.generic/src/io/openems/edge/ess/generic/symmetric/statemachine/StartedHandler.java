package io.openems.edge.ess.generic.symmetric.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.ess.generic.common.GenericManagedEss;
import io.openems.edge.ess.generic.symmetric.statemachine.StateMachine.State;

public class StartedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		GenericManagedEss ess = context.getParent();

		if (ess.hasFaults()) {
			return State.UNDEFINED;
		}

		if (!context.battery.isStarted()) {
			return State.UNDEFINED;
		}

		if (!context.batteryInverter.isStarted()) {
			return State.UNDEFINED;
		}

		// Mark as started
		ess._setStartStop(StartStop.START);

		return State.STARTED;
	}

}

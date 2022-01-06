package io.openems.edge.battery.bydcommercial.statemachine;

import io.openems.edge.battery.bydcommercial.BatteryBoxC130;
import io.openems.edge.battery.bydcommercial.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		BatteryBoxC130 battery = context.getParent();

		switch (battery.getStartStopTarget()) {
		case UNDEFINED:
			// Stuck in UNDEFINED State
			return State.UNDEFINED;

		case START:
			// force START
			if (battery.hasFaults()) {
				// Has Faults -> error handling
				return State.ERROR;
			} else {
				// No Faults -> start
				return State.GO_RUNNING;
			}

		case STOP:
			// force STOP
			return State.GO_STOPPED;
		}

		assert false;
		return State.UNDEFINED; // can never happen
	}

}

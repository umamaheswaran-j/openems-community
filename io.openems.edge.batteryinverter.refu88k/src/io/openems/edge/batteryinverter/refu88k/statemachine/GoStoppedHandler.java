package io.openems.edge.batteryinverter.refu88k.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.batteryinverter.refu88k.RefuStore88k;
import io.openems.edge.batteryinverter.refu88k.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class GoStoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		RefuStore88k inverter = context.getParent();

		switch (inverter.getOperatingState()) {
		case STARTING:
		case MPPT:
		case THROTTLED:
		case STARTED:
			inverter.stopInverter();
			return State.GO_STOPPED;
		case FAULT:
		case STANDBY:
			return State.STOPPED;
		case SHUTTING_DOWN:
		case OFF:
		case SLEEPING:
		case UNDEFINED:
			return State.UNDEFINED;
		}

		return State.UNDEFINED;
	}
}

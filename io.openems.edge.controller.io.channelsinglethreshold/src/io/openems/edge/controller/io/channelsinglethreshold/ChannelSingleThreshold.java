package io.openems.edge.controller.io.channelsinglethreshold;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

public interface ChannelSingleThreshold extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		AWAITING_HYSTERESIS(Doc.of(Level.INFO) //
				.text("Would change State, but hystesis is active")); //

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#AWAITING_HYSTERESIS}.
	 * 
	 * @return the Channel
	 */
	public default StateChannel getAwaitingHysteresisChannel() {
		return this.channel(ChannelId.AWAITING_HYSTERESIS);
	}

	/**
	 * Gets the Run-Failed State. See {@link ChannelId#AWAITING_HYSTERESIS}.
	 * 
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getAwaitingHysteresis() {
		return this.getAwaitingHysteresisChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#AWAITING_HYSTERESIS} Channel.
	 * 
	 * @param value the next value
	 */
	public default void _setAwaitingHysteresis(boolean value) {
		this.getAwaitingHysteresisChannel().setNextValue(value);
	}

}

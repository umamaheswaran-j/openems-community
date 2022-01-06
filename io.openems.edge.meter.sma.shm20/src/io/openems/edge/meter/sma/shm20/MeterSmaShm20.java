package io.openems.edge.meter.sma.shm20;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;

public interface MeterSmaShm20 {
	
	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		ACTIVE_PRODUCTION_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_CONSUMPTION_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_CONSUMPTION_POWER_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_CONSUMPTION_POWER_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_CONSUMPTION_POWER_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_PRODUCTION_POWER_L1(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_PRODUCTION_POWER_L2(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //
		ACTIVE_PRODUCTION_POWER_L3(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)), //		
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		public Doc doc() {
			return this.doc;
		}
	}
	
}

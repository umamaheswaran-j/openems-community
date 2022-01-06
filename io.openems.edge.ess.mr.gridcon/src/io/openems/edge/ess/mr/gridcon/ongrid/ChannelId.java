package io.openems.edge.ess.mr.gridcon.ongrid;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.ess.mr.gridcon.state.ongrid.OnGridState;

public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
	STATE_MACHINE(Doc.of(OnGridState.values()).text("Current StateObject of StateObject-Machine"));

	private final Doc doc;

	private ChannelId(Doc doc) {
		this.doc = doc;
	}

	@Override
	public Doc doc() {
		return this.doc;
	}
}
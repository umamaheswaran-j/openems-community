package io.openems.backend.edgewebsocket;

import java.util.Optional;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.backend.common.metadata.Edge;
import io.openems.common.exceptions.OpenemsException;

public class OnClose implements io.openems.common.websocket.OnClose {

	private final Logger log = LoggerFactory.getLogger(OnClose.class);
	private final EdgeWebsocketImpl parent;

	public OnClose(EdgeWebsocketImpl parent) {
		this.parent = parent;
	}

	@Override
	public void run(WebSocket ws, int code, String reason, boolean remote) throws OpenemsException {
		// get edgeId from websocket
		WsData wsData = ws.getAttachment();
		Optional<String> edgeIdOpt = wsData.getEdgeId();
		String edgeId;
		if (edgeIdOpt.isPresent()) {
			edgeId = edgeIdOpt.get();
			Optional<Edge> edgeOpt = this.parent.metadata.getEdge(edgeId);
			// if there is no other websocket connection for this edgeId -> announce Edge as
			// offline
			if (edgeOpt.isPresent()) {
				boolean isOnline = this.parent.isOnline(edgeId);
				edgeOpt.get().setOnline(isOnline);
			}

		} else {
			edgeId = "UNKNOWN";
		}

		// TODO send notification, to UI

		if (code == CloseFrame.TRY_AGAIN_LATER) {
			// This happens when Metadata service is not yet initialized. No need to log
			// message.
		} else {
			this.parent.logInfo(this.log,
					"Edge [" + edgeId + "] disconnected. Code [" + code + "] Reason [" + reason + "]");
		}
	}

}

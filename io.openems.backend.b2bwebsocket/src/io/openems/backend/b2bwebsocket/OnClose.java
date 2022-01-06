package io.openems.backend.b2bwebsocket;

import java.util.Optional;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.backend.common.metadata.User;
import io.openems.common.exceptions.OpenemsException;

public class OnClose implements io.openems.common.websocket.OnClose {

	private final Logger log = LoggerFactory.getLogger(OnClose.class);
	private final B2bWebsocket parent;

	public OnClose(B2bWebsocket parent) {
		this.parent = parent;
	}

	@Override
	public void run(WebSocket ws, int code, String reason, boolean remote) throws OpenemsException {
		WsData wsData = ws.getAttachment();
		Optional<User> user = wsData.getUserOpt();
		if (user.isPresent()) {
			this.parent.logInfo(this.log, "User [" + user.get().getName() + "] closed connection");
		} else {
			this.parent.logInfo(this.log, "Connection closed");
		}
	}

}

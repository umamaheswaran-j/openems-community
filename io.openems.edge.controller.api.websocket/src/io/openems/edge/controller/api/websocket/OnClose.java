package io.openems.edge.controller.api.websocket;

import java.util.Optional;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.user.User;

public class OnClose implements io.openems.common.websocket.OnClose {

	private final Logger log = LoggerFactory.getLogger(OnClose.class);
	private final WebsocketApi parent;

	public OnClose(WebsocketApi parent) {
		this.parent = parent;
	}

	@Override
	public void run(WebSocket ws, int code, String reason, boolean remote) throws OpenemsException {
		// get websocket attachment
		WsData wsData = ws.getAttachment();
		Optional<User> user = wsData.getUser();

		// print log message
		String logMessage;
		if (user.isPresent()) {
			logMessage = "User [" + user.get() + "] closed websocket connection.";
		} else {
			logMessage = "Unknown User [" + wsData.getSessionToken() + "] closed websocket connection.";
		}
		this.parent.logInfo(this.log, logMessage);
	}

}

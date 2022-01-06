package io.openems.edge.controller.api.websocket;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.GenericJsonrpcResponseSuccess;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.jsonrpc.notification.EdgeRpcNotification;
import io.openems.common.jsonrpc.notification.SystemLogNotification;
import io.openems.common.jsonrpc.request.SubscribeSystemLogRequest;

public class SystemLogHandler {

	private final Logger log = LoggerFactory.getLogger(SystemLogHandler.class);
	private final WebsocketApi parent;
	private final Set<String> subscriptions = new HashSet<>();

	public SystemLogHandler(WebsocketApi parent) {
		this.parent = parent;
	}

	/**
	 * Handles a {@link SubscribeSystemLogRequest}.
	 * 
	 * @param token   the UI session token
	 * @param request the {@link SubscribeSystemLogRequest}
	 * @return a reply
	 * @throws OpenemsNamedException on error
	 */
	public CompletableFuture<JsonrpcResponseSuccess> handleSubscribeSystemLogRequest(String token,
			SubscribeSystemLogRequest request) throws OpenemsNamedException {
		if (request.getSubscribe()) {
			/*
			 * Start subscription
			 */
			this.subscriptions.add(token);

		} else {
			/*
			 * End subscription
			 */
			this.subscriptions.remove(token);
		}
		// announce success
		return CompletableFuture.completedFuture(new GenericJsonrpcResponseSuccess(request.getId()));
	}

	/**
	 * Handles a PaxLoggingEvent and sends a SystemLogNotification to all subscribed
	 * UI sessions.
	 * 
	 * @param event the event
	 */
	public void handlePaxLoggingEvent(PaxLoggingEvent event) {
		synchronized (this.subscriptions) {
			if (this.subscriptions.isEmpty()) {
				return;
			}
			EdgeRpcNotification notification = new EdgeRpcNotification(WebsocketApi.EDGE_ID,
					SystemLogNotification.fromPaxLoggingEvent(event));
			for (Iterator<String> iter = this.subscriptions.iterator(); iter.hasNext();) {
				String token = iter.next();
				try {
					this.parent.getWsDataForTokenOrError(token).send(notification);
				} catch (OpenemsNamedException e) {
					iter.remove();
					this.log.warn("Unable to handle PaxLoggingEvent: " + e.getMessage());
				}
			}
		}
	}
}

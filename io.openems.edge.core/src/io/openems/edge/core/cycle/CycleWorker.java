package io.openems.edge.core.cycle;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import info.faljse.SDNotify.SDNotify;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.worker.AbstractWorker;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.scheduler.api.Scheduler;

public class CycleWorker extends AbstractWorker {

	private final Logger log = LoggerFactory.getLogger(CycleWorker.class);
	private final CycleImpl parent;

	public CycleWorker(CycleImpl parent) {
		this.parent = parent;
	}

	@Override
	protected int getCycleTime() {
		return this.parent.getCycleTime();
	}

	@Override
	protected void forever() {
		// Prepare Cycle-Time measurement
		Stopwatch stopwatch = Stopwatch.createStarted();

		// Kick Operating System Watchdog
		String socketName = System.getenv().get("NOTIFY_SOCKET");
		if (socketName != null && socketName.length() != 0) {
			if (SDNotify.isAvailable()) {
				SDNotify.sendWatchdog();
			}
		}

		try {
			/*
			 * Trigger BEFORE_PROCESS_IMAGE event
			 */
			this.parent.eventAdmin
					.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, new HashMap<>()));

			/*
			 * Before Controllers start: switch to next process image for each channel
			 */
			this.parent.componentManager.getEnabledComponents().stream() //
					.filter(c -> c.isEnabled() && !(c instanceof Sum)) //
					.forEach(component -> {
						component.channels().forEach(channel -> {
							channel.nextProcessImage();
						});
					});
			this.parent.channels().forEach(channel -> {
				channel.nextProcessImage();
			});

			/*
			 * Update the Channels in the Sum-Component.
			 */
			this.parent.sumComponent.updateChannelsBeforeProcessImage();
			this.parent.sumComponent.channels().forEach(channel -> {
				channel.nextProcessImage();
			});

			/*
			 * Trigger AFTER_PROCESS_IMAGE event
			 */
			this.parent.eventAdmin
					.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE, new HashMap<>()));

			/*
			 * Trigger BEFORE_CONTROLLERS event
			 */
			this.parent.eventAdmin
					.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_BEFORE_CONTROLLERS, new HashMap<>()));

			boolean hasDisabledController = false;

			/*
			 * Execute Schedulers and their Controllers
			 */
			if (this.parent.schedulers.isEmpty()) {
				this.parent.logWarn(this.log, "There are no Schedulers configured!");
			} else {
				for (Scheduler scheduler : this.parent.schedulers) {
					boolean schedulerControllerIsMissing = false;

					for (String controllerId : scheduler.getControllers()) {
						Controller controller;
						try {
							controller = this.parent.componentManager.getPossiblyDisabledComponent(controllerId);

						} catch (OpenemsNamedException e) {
							this.parent.logWarn(this.log, "Scheduler [" + scheduler.id() + "]: Controller ["
									+ controllerId + "] is missing. " + e.getMessage());
							schedulerControllerIsMissing = true;
							continue;
						}

						if (!controller.isEnabled()) {
							hasDisabledController = true;
							continue;
						}

						try {
							// Execute Controller logic
							controller.run();

							// announce running was ok
							controller._setRunFailed(false);

						} catch (OpenemsNamedException e) {
							this.parent.logWarn(this.log,
									"Error in Controller [" + controller.id() + "]: " + e.getMessage());

							// announce running failed
							controller._setRunFailed(true);

						} catch (Exception e) {
							this.parent.logWarn(this.log, "Error in Controller [" + controller.id() + "]. "
									+ e.getClass().getSimpleName() + ": " + e.getMessage());
							if (e instanceof ClassCastException || e instanceof NullPointerException
									|| e instanceof IllegalArgumentException) {
								e.printStackTrace();
							}
							// announce running failed
							controller._setRunFailed(true);
						}
					}

					// announce Scheduler Controller is missing
					scheduler._setControllerIsMissing(schedulerControllerIsMissing);
				}
			}

			// announce ignoring disabled Controllers.
			this.parent._setIgnoreDisabledController(hasDisabledController);

			/*
			 * Trigger AFTER_CONTROLLERS event
			 */
			this.parent.eventAdmin
					.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_AFTER_CONTROLLERS, new HashMap<>()));

			/*
			 * Trigger BEFORE_WRITE event
			 */
			this.parent.eventAdmin.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_BEFORE_WRITE, new HashMap<>()));

			/*
			 * Trigger EXECUTE_WRITE event
			 */
			this.parent.eventAdmin.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE, new HashMap<>()));

			/*
			 * Trigger AFTER_WRITE event
			 */
			this.parent.eventAdmin.sendEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE, new HashMap<>()));

		} catch (Throwable t) {
			this.parent.logWarn(this.log,
					"Error in Scheduler. " + t.getClass().getSimpleName() + ": " + t.getMessage());
			if (t instanceof ClassCastException || t instanceof NullPointerException) {
				t.printStackTrace();
			}
		}

		// Measure actual Cycle-Time
		this.parent._setMeasuredCycleTime(stopwatch.elapsed(TimeUnit.MILLISECONDS));
	}

}

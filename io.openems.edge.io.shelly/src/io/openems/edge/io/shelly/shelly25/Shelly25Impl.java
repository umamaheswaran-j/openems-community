package io.openems.edge.io.shelly.shelly25;

import java.util.Objects;
import java.util.Optional;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.channel.BooleanWriteChannel;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.io.api.DigitalOutput;
import io.openems.edge.io.shelly.common.ShellyApi;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "IO.Shelly.25", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE //
		})
public class Shelly25Impl extends AbstractOpenemsComponent
		implements Shelly25, DigitalOutput, OpenemsComponent, EventHandler {

	private final Logger log = LoggerFactory.getLogger(Shelly25Impl.class);

	private final BooleanWriteChannel[] digitalOutputChannels;
	private ShellyApi shellyApi = null;

	public Shelly25Impl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				DigitalOutput.ChannelId.values(), //
				Shelly25.ChannelId.values() //
		);
		this.digitalOutputChannels = new BooleanWriteChannel[] { //
				this.channel(Shelly25.ChannelId.RELAY_1), //
				this.channel(Shelly25.ChannelId.RELAY_2), //
		};
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.shellyApi = new ShellyApi(config.ip());
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public BooleanWriteChannel[] digitalOutputChannels() {
		return this.digitalOutputChannels;
	}

	@Override
	public String debugLog() {
		StringBuilder b = new StringBuilder();
		int i = 1;
		for (WriteChannel<Boolean> channel : this.digitalOutputChannels) {
			String valueText;
			Optional<Boolean> valueOpt = channel.value().asOptional();
			if (valueOpt.isPresent()) {
				valueText = valueOpt.get() ? "x" : "-";
			} else {
				valueText = "?";
			}
			b.append(i + valueText);

			// add space for all but the last
			if (++i <= this.digitalOutputChannels.length) {
				b.append(" ");
			}
		}
		return b.toString();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}

		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.eventBeforeProcessImage();
			break;

		case EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE:
			this.eventExecuteWrite();
			break;
		}
	}

	/**
	 * Execute on Cycle Event "Before Process Image".
	 */
	private void eventBeforeProcessImage() {
		Boolean relay1IsOn;
		Boolean relay2IsOn;
		try {
			JsonObject json = this.shellyApi.getStatus();
			JsonArray relays = JsonUtils.getAsJsonArray(json, "relays");
			JsonObject relay1 = JsonUtils.getAsJsonObject(relays.get(0));
			relay1IsOn = JsonUtils.getAsBoolean(relay1, "ison");
			JsonObject relay2 = JsonUtils.getAsJsonObject(relays.get(1));
			relay2IsOn = JsonUtils.getAsBoolean(relay2, "ison");

			this._setSlaveCommunicationFailed(false);

		} catch (OpenemsNamedException | IndexOutOfBoundsException e) {
			relay1IsOn = null;
			relay2IsOn = null;
			this.logError(this.log, "Unable to read from Shelly API: " + e.getMessage());
			this._setSlaveCommunicationFailed(true);
		}
		this._setRelay1(relay1IsOn);
		this._setRelay2(relay2IsOn);
	}

	/**
	 * Execute on Cycle Event "Execute Write".
	 */
	private void eventExecuteWrite() {
		try {
			this.executeWrite(this.getRelay1Channel(), 0);
			this.executeWrite(this.getRelay2Channel(), 1);

			this._setSlaveCommunicationFailed(false);
		} catch (OpenemsNamedException e) {
			this._setSlaveCommunicationFailed(true);
		}
	}

	private void executeWrite(BooleanWriteChannel channel, int index) throws OpenemsNamedException {
		Boolean readValue = channel.value().get();
		Optional<Boolean> writeValue = channel.getNextWriteValueAndReset();
		if (!writeValue.isPresent()) {
			// no write value
			return;
		}
		if (Objects.equals(readValue, writeValue.get())) {
			// read value = write value
			return;
		}
		this.shellyApi.setRelayTurn(index, writeValue.get());
	}

}
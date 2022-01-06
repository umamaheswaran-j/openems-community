package io.openems.edge.controller.io.alarm;

import java.util.Optional;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.controller.api.Controller;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.IO.Alarm", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)
public class IoAlarm extends AbstractOpenemsComponent implements Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(IoAlarm.class);

	@Reference
	protected ComponentManager componentManager;

	private Config config;

	public IoAlarm() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ChannelId.values() //
		);
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws IllegalArgumentException, OpenemsNamedException {
		boolean setOutput = false;

		for (String channelAddress : this.config.inputChannelAddress()) {
			StateChannel channel = this.componentManager.getChannel(ChannelAddress.fromString(channelAddress));
			// Reading the value of all input channels
			boolean isStateChannelSet = TypeUtils.getAsType(OpenemsType.BOOLEAN, channel.value().getOrError());

			if (isStateChannelSet) {
				// If Channel was set: signal true
				setOutput = true;
				break;
			}
		}

		// Set Output Channel
		WriteChannel<Boolean> outputChannel = this.componentManager
				.getChannel(ChannelAddress.fromString(this.config.outputChannelAddress()));
		Optional<Boolean> currentValueOpt = outputChannel.value().asOptional();
		if (!currentValueOpt.isPresent() || currentValueOpt.get() != setOutput) {
			this.logInfo(this.log, "Set output [" + outputChannel.address() + "] " + setOutput + ".");
			outputChannel.setNextWriteValue(setOutput);
		}
	}
}

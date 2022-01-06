package io.openems.edge.bosch.bpts5hybrid.core;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.bosch.bpts5hybrid.ess.BoschBpts5HybridEss;
import io.openems.edge.bosch.bpts5hybrid.meter.BoschBpts5HybridMeter;
import io.openems.edge.bosch.bpts5hybrid.pv.BoschBpts5HybridPv;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Bosch.BPTS5Hybrid.Core", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
		} //
)
public class BoschBpts5HybridCoreImpl extends AbstractOpenemsComponent
		implements BoschBpts5HybridCore, OpenemsComponent, EventHandler {

	@Reference
	private ConfigurationAdmin cm;

	private BoschBpts5HybridReadWorker worker = null;

	private AtomicReference<BoschBpts5HybridEss> ess = new AtomicReference<>();
	private AtomicReference<BoschBpts5HybridPv> pv = new AtomicReference<>();
	private AtomicReference<BoschBpts5HybridMeter> meter = new AtomicReference<>();

	public BoschBpts5HybridCoreImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				CoreChannelId.values());
	}

	@Activate
	void activate(ComponentContext context, Config config) throws ConfigurationException, IOException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.worker = new BoschBpts5HybridReadWorker(this, config.ipaddress(), config.interval());
		this.worker.activate(config.id());
	}

	@Deactivate
	protected void deactivate() {
		if (this.worker != null) {
			this.worker.deactivate();
		}
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || this.worker == null) {
			return;
		}

		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
			this.worker.triggerNextRun();
			break;
		}
	}

	public void setEss(BoschBpts5HybridEss boschBpts5HybridEss) {
		this.ess.set(boschBpts5HybridEss);
	}

	public Optional<BoschBpts5HybridEss> getEss() {
		return Optional.ofNullable(ess.get());
	}

	@Override
	public void setPv(BoschBpts5HybridPv boschBpts5HybridPv) {
		this.pv.set(boschBpts5HybridPv);
	}

	@Override
	public Optional<BoschBpts5HybridPv> getPv() {
		return Optional.ofNullable(pv.get());
	}

	@Override
	public void setMeter(BoschBpts5HybridMeter boschBpts5HybridMeter) {
		this.meter.set(boschBpts5HybridMeter);
	}

	@Override
	public Optional<BoschBpts5HybridMeter> getMeter() {
		return Optional.ofNullable(meter.get());
	}
}

package io.openems.edge.controller.ess.hybrid.surplusfeedtogrid;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.Hybrid.Surplus-Feed-To-Grid", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class SurplusFeedToGridControllerImpl extends AbstractOpenemsComponent
		implements SurplusFeedToGridController, Controller, OpenemsComponent {

	@Reference
	protected ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private HybridEss ess;

	public SurplusFeedToGridControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				SurplusFeedToGridController.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());

		// update filter for 'ess'
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "ess", config.ess_id())) {
			return;
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		Integer surplusPower = this.ess.getSurplusPower();

		// No surplus power by Ess? -> stop
		if (surplusPower == null) {
			this._setSurplusFeedToGridIsLimited(false);
			return;
		}

		ManagedSymmetricEss managedEss = (ManagedSymmetricEss) this.ess;

		// Get maximum possible surplus feed-in power
		int maxDischargePower = managedEss.getPower().getMaxPower(managedEss, Phase.ALL, Pwr.ACTIVE);

		// Is surplus power limited by a higher priority Controller? -> set info state
		final int minDischargePower;
		if (maxDischargePower > surplusPower) {
			this._setSurplusFeedToGridIsLimited(false);
			minDischargePower = surplusPower;
		} else {
			this._setSurplusFeedToGridIsLimited(true);
			minDischargePower = maxDischargePower;
		}

		// Set surplus feed-in power set-point
		managedEss.setActivePowerGreaterOrEquals(minDischargePower);
	}
}

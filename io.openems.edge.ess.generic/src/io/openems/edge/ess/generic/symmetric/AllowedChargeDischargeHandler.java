package io.openems.edge.ess.generic.symmetric;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.generic.common.AbstractAllowedChargeDischargeHandler;

public class AllowedChargeDischargeHandler extends AbstractAllowedChargeDischargeHandler<GenericManagedSymmetricEss> {

	public AllowedChargeDischargeHandler(GenericManagedSymmetricEss parent) {
		super(parent);
	}

	@Override
	public void accept(ClockProvider clockProvider, Battery battery) {
		this.calculateAllowedChargeDischargePower(clockProvider, battery);

		// Battery limits
		int batteryAllowedChargePower = Math.round(this.lastBatteryAllowedChargePower);
		int batteryAllowedDischargePower = Math.round(this.lastBatteryAllowedDischargePower);

		// PV-Production (for HybridEss)
		int pvProduction = Math.max(//
				TypeUtils.orElse(//
						TypeUtils.subtract(this.parent.getActivePower().get(), this.parent.getDcDischargePower().get()), //
						0),
				0);

		// Apply AllowedChargePower and AllowedDischargePower
		this.parent._setAllowedChargePower(batteryAllowedChargePower * -1 /* invert charge power */);
		this.parent._setAllowedDischargePower(batteryAllowedDischargePower + pvProduction);
	}

}

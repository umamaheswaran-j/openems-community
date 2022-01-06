package io.openems.edge.batteryinverter.api;

import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.ess.power.api.Relationship;

/**
 * Wraps a Constraint for a {@link ManagedSymmetricBatteryInverter}.
 */
public class BatteryInverterConstraint {

	public final String description;
	public final Phase phase;
	public final Pwr pwr;
	public final Relationship relationship;
	public final double value;

	public static BatteryInverterConstraint[] NO_CONSTRAINTS = new BatteryInverterConstraint[] {};

	public BatteryInverterConstraint(String description, Phase phase, Pwr pwr, Relationship relationship,
			double value) {
		this.description = description;
		this.phase = phase;
		this.pwr = pwr;
		this.relationship = relationship;
		this.value = value;
	}

}
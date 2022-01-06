package io.openems.edge.battery.soltaro.cluster.versionb;

import io.openems.common.utils.ConfigUtils;
import io.openems.edge.battery.soltaro.common.enums.BatteryState;
import io.openems.edge.battery.soltaro.common.enums.ModuleType;
import io.openems.edge.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = null;
		private String modbusId = null;
		public int modbusUnitId;
		public int numberOfSlaves;
		public ModuleType moduleType;
		public int watchdog;
		public BatteryState batteryState;
		public int[] racks;
		public int errorLevel2Delay;
		public int maxStartAppempts;
		public int maxStartTime;
		public int pendingTolerance;
		public int startUnsuccessfulDelay;
		public int minimalCellVoltage;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}

		public Builder setModbusUnitId(int modbusUnitId) {
			this.modbusUnitId = modbusUnitId;
			return this;
		}

		public Builder setNumberOfSlaves(int numberOfSlaves) {
			this.numberOfSlaves = numberOfSlaves;
			return this;
		}

		public Builder setModuleType(ModuleType moduleType) {
			this.moduleType = moduleType;
			return this;
		}

		public Builder setWatchdog(int watchdog) {
			this.watchdog = watchdog;
			return this;
		}

		public Builder setBatteryState(BatteryState batteryState) {
			this.batteryState = batteryState;
			return this;
		}

		public Builder setRacks(int... racks) {
			this.racks = racks;
			return this;
		}

		public Builder setErrorLevel2Delay(int errorLevel2Delay) {
			this.errorLevel2Delay = errorLevel2Delay;
			return this;
		}

		public Builder setMaxStartAppempts(int maxStartAppempts) {
			this.maxStartAppempts = maxStartAppempts;
			return this;
		}

		public Builder setMaxStartTime(int maxStartTime) {
			this.maxStartTime = maxStartTime;
			return this;
		}

		public Builder setPendingTolerance(int pendingTolerance) {
			this.pendingTolerance = pendingTolerance;
			return this;
		}

		public Builder setStartUnsuccessfulDelay(int startUnsuccessfulDelay) {
			this.startUnsuccessfulDelay = startUnsuccessfulDelay;
			return this;
		}

		public Builder setMinimalCellVoltage(int minimalCellVoltage) {
			this.minimalCellVoltage = minimalCellVoltage;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String modbus_id() {
		return this.builder.modbusId;
	}

	@Override
	public String Modbus_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.modbus_id());
	}

	@Override
	public int modbusUnitId() {
		return this.builder.modbusUnitId;
	}

	@Override
	public int numberOfSlaves() {
		return this.builder.numberOfSlaves;
	}

	@Override
	public ModuleType moduleType() {
		return this.builder.moduleType;
	}

	@Override
	public BatteryState batteryState() {
		return this.builder.batteryState;
	}

	@Override
	public int[] racks() {
		return this.builder.racks;
	}

	@Override
	public int errorLevel2Delay() {
		return this.builder.errorLevel2Delay;
	}

	@Override
	public int maxStartAppempts() {
		return this.builder.maxStartAppempts;
	}

	@Override
	public int maxStartTime() {
		return this.builder.maxStartTime;
	}

	@Override
	public int pendingTolerance() {
		return this.builder.pendingTolerance;
	}

	@Override
	public int startUnsuccessfulDelay() {
		return this.builder.startUnsuccessfulDelay;
	}

	@Override
	public int minimalCellVoltage() {
		return this.builder.minimalCellVoltage;
	}

}
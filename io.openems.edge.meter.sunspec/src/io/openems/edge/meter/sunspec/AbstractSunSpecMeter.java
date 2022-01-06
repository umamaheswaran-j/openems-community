package io.openems.edge.meter.sunspec;

import java.util.Map;
import java.util.Optional;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.sunspec.AbstractOpenemsSunSpecComponent;
import io.openems.edge.bridge.modbus.sunspec.DefaultSunSpecModel;
import io.openems.edge.bridge.modbus.sunspec.SunSpecModel;
import io.openems.edge.bridge.modbus.sunspec.SunSpecPoint;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.AsymmetricMeter;
import io.openems.edge.meter.api.SymmetricMeter;

public abstract class AbstractSunSpecMeter extends AbstractOpenemsSunSpecComponent
		implements AsymmetricMeter, SymmetricMeter, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(AbstractSunSpecMeter.class);

	public AbstractSunSpecMeter(Map<SunSpecModel, Priority> activeModels,
			io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
			io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) throws OpenemsException {
		super(activeModels, firstInitialChannelIds, furtherInitialChannelIds);
	}

	/**
	 * Make sure to call this method from the inheriting OSGi Component.
	 * 
	 * @throws OpenemsException on error
	 */
	@Override
	protected boolean activate(ComponentContext context, String id, String alias, boolean enabled, int unitId,
			ConfigurationAdmin cm, String modbusReference, String modbusId, int readFromCommonBlockNo)
			throws OpenemsException {
		return super.activate(context, id, alias, enabled, unitId, cm, modbusReference, modbusId,
				readFromCommonBlockNo);
	}

	/**
	 * Make sure to call this method from the inheriting OSGi Component.
	 */
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	@Override
	protected void onSunSpecInitializationCompleted() {
		this.logInfo(this.log, "SunSpec initialization finished. " + this.channels().size() + " Channels available.");

		/*
		 * SymmetricMeter
		 */
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.FREQUENCY, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.HZ, DefaultSunSpecModel.S203.HZ, DefaultSunSpecModel.S202.HZ,
				DefaultSunSpecModel.S201.HZ);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.ACTIVE_POWER, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.W, DefaultSunSpecModel.S203.W, DefaultSunSpecModel.S202.W,
				DefaultSunSpecModel.S201.W);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.REACTIVE_POWER, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.VAR, DefaultSunSpecModel.S203.VAR, DefaultSunSpecModel.S202.VAR,
				DefaultSunSpecModel.S201.VAR);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.TOT_WH_IMP, DefaultSunSpecModel.S203.TOT_WH_IMP,
				DefaultSunSpecModel.S202.TOT_WH_IMP, DefaultSunSpecModel.S201.TOT_WH_IMP);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.TOT_WH_EXP, DefaultSunSpecModel.S203.TOT_WH_EXP,
				DefaultSunSpecModel.S202.TOT_WH_EXP, DefaultSunSpecModel.S201.TOT_WH_EXP);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.VOLTAGE, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.PH_V, DefaultSunSpecModel.S203.PH_V, DefaultSunSpecModel.S202.PH_V,
				DefaultSunSpecModel.S201.PH_V, //
				DefaultSunSpecModel.S204.PH_VPH_A, DefaultSunSpecModel.S203.PH_VPH_A, DefaultSunSpecModel.S202.PH_VPH_A,
				DefaultSunSpecModel.S201.PH_VPH_A, //
				DefaultSunSpecModel.S204.PH_VPH_B, DefaultSunSpecModel.S203.PH_VPH_B, DefaultSunSpecModel.S202.PH_VPH_B,
				DefaultSunSpecModel.S201.PH_VPH_B, //
				DefaultSunSpecModel.S204.PH_VPH_C, DefaultSunSpecModel.S203.PH_VPH_C, DefaultSunSpecModel.S202.PH_VPH_C,
				DefaultSunSpecModel.S201.PH_VPH_C);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.CURRENT, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.A, DefaultSunSpecModel.S203.A, DefaultSunSpecModel.S202.A,
				DefaultSunSpecModel.S201.A);

		/*
		 * AsymmetricMeter
		 */
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.ACTIVE_POWER_L1, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.WPH_A, DefaultSunSpecModel.S203.WPH_A, DefaultSunSpecModel.S202.WPH_A,
				DefaultSunSpecModel.S201.WPH_A);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.ACTIVE_POWER_L2, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.WPH_B, DefaultSunSpecModel.S203.WPH_B, DefaultSunSpecModel.S202.WPH_B,
				DefaultSunSpecModel.S201.WPH_B);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.ACTIVE_POWER_L3, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.WPH_C, DefaultSunSpecModel.S203.WPH_C, DefaultSunSpecModel.S202.WPH_C,
				DefaultSunSpecModel.S201.WPH_C);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.CURRENT_L1, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.APH_A, DefaultSunSpecModel.S203.APH_A, DefaultSunSpecModel.S202.APH_A,
				DefaultSunSpecModel.S201.APH_A);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.CURRENT_L2, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.APH_B, DefaultSunSpecModel.S203.APH_B, DefaultSunSpecModel.S202.APH_B,
				DefaultSunSpecModel.S201.APH_B);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.CURRENT_L3, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.APH_C, DefaultSunSpecModel.S203.APH_C, DefaultSunSpecModel.S202.APH_C,
				DefaultSunSpecModel.S201.APH_C);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.REACTIVE_POWER_L1, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.V_A_RPH_A, DefaultSunSpecModel.S203.V_A_RPH_A,
				DefaultSunSpecModel.S202.V_A_RPH_A, DefaultSunSpecModel.S201.V_A_RPH_A);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.REACTIVE_POWER_L2, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.V_A_RPH_B, DefaultSunSpecModel.S203.V_A_RPH_B,
				DefaultSunSpecModel.S202.V_A_RPH_B, DefaultSunSpecModel.S201.V_A_RPH_B);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.REACTIVE_POWER_L3, //
				ElementToChannelConverter.INVERT, //
				DefaultSunSpecModel.S204.V_A_RPH_C, DefaultSunSpecModel.S203.V_A_RPH_C,
				DefaultSunSpecModel.S202.V_A_RPH_C, DefaultSunSpecModel.S201.V_A_RPH_C);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.VOLTAGE_L1, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.PH_VPH_A, DefaultSunSpecModel.S203.PH_VPH_A, DefaultSunSpecModel.S202.PH_VPH_A,
				DefaultSunSpecModel.S201.PH_VPH_A);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.VOLTAGE_L2, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.PH_VPH_B, DefaultSunSpecModel.S203.PH_VPH_B, DefaultSunSpecModel.S202.PH_VPH_B,
				DefaultSunSpecModel.S201.PH_VPH_B);
		this.mapFirstPointToChannel(//
				AsymmetricMeter.ChannelId.VOLTAGE_L3, //
				ElementToChannelConverter.SCALE_FACTOR_3, //
				DefaultSunSpecModel.S204.PH_VPH_C, DefaultSunSpecModel.S203.PH_VPH_C, DefaultSunSpecModel.S202.PH_VPH_C,
				DefaultSunSpecModel.S201.PH_VPH_C);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, //
				ElementToChannelConverter.DIRECT_1_TO_1, //
				DefaultSunSpecModel.S204.TOT_WH_EXP, DefaultSunSpecModel.S203.TOT_WH_EXP,
				DefaultSunSpecModel.S202.TOT_WH_EXP, DefaultSunSpecModel.S201.TOT_WH_EXP);
		this.mapFirstPointToChannel(//
				SymmetricMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, //
				ElementToChannelConverter.DIRECT_1_TO_1, //
				DefaultSunSpecModel.S204.TOT_WH_IMP, DefaultSunSpecModel.S203.TOT_WH_IMP,
				DefaultSunSpecModel.S202.TOT_WH_IMP, DefaultSunSpecModel.S201.TOT_WH_IMP);
	}

	@Override
	protected <T extends Channel<?>> Optional<T> getSunSpecChannel(SunSpecPoint point) {
		return super.getSunSpecChannel(point);
	}

	@Override
	protected <T extends Channel<?>> T getSunSpecChannelOrError(SunSpecPoint point) throws OpenemsException {
		return super.getSunSpecChannelOrError(point);
	}
}

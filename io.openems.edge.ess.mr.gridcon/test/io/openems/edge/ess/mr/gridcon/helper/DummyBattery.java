package io.openems.edge.ess.mr.gridcon.helper;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.startstop.StartStop;

public class DummyBattery extends io.openems.edge.battery.test.DummyBattery implements Battery {

	public static int DEFAULT_SOC = 50;
	public static int DEFAULT_SOH = 99;
	public static int DEFAULT_CAPACITY = 50_000;
	public static int DEFAULT_MIN_CELL_VOLTAGE = 3280;
	public static int DEFAULT_MAX_CELL_VOLTAGE = 3380;
	public static int DEFAULT_MIN_CELL_TEMPERATURE = 25;
	public static int DEFAULT_MAX_CELL_TEMPERATURE = 33;

	public static int DEFAULT_VOLTAGE = 800;
	public static int DEFAULT_CURRENT = 0;
	public static int DEFAULT_MAX_CHARGE_CURRENT = 80;
	public static int DEFAULT_MAX_DISCHARGE_CURRENT = 60;

	public static int DEFAULT_MIN_VOLTAGE = 700;
	public static int DEFAULT_MAX_VOLTAGE = 900;

//	private boolean running = false;

	public DummyBattery(//
	) { //
		super("battery0");

		setMinimalCellVoltage(DEFAULT_MIN_CELL_VOLTAGE);
		setMaximalCellVoltage(DEFAULT_MAX_CELL_VOLTAGE);
		setMinimalCellTemperature(DEFAULT_MIN_CELL_TEMPERATURE);
		setMaximalCellTemperature(DEFAULT_MAX_CELL_TEMPERATURE);
		setSoc(DEFAULT_SOC);
		setSoh(DEFAULT_SOH);
		setCapacity(DEFAULT_CAPACITY);
		setVoltage(DEFAULT_VOLTAGE);
		setCurrent(DEFAULT_CURRENT);
		setMaximalChargeCurrent(DEFAULT_MAX_CHARGE_CURRENT);
		setMaximalDischargeCurrent(DEFAULT_MAX_DISCHARGE_CURRENT);
		setDischargeMinVoltage(DEFAULT_MIN_VOLTAGE);
		setChargeMaxVoltage(DEFAULT_MAX_VOLTAGE);
	}

	public void setMinimalCellVoltage(int minimalCellVoltage) {
		this._setMinCellVoltage(minimalCellVoltage);
		this.getMinCellVoltageChannel().nextProcessImage();
	}

	public void setMinimalCellVoltageToUndefined() {
		this._setMinCellVoltage(null);
		this.getMinCellVoltageChannel().nextProcessImage();
	}

	public void setMaximalCellVoltage(int maximalCellVoltage) {
		this._setMaxCellVoltage(maximalCellVoltage);
		this.getMaxCellVoltageChannel().nextProcessImage();
	}

	public void setMaximalCellVoltageToUndefined() {
		this._setMaxCellVoltage(null);
		this.getMaxCellVoltageChannel().nextProcessImage();
	}

	public void setMinimalCellTemperature(int minimalCellTemperature) {
		this._setMinCellTemperature(minimalCellTemperature);
		this.getMinCellTemperatureChannel().nextProcessImage();
	}

	public void setMinimalCellTemperatureToUndefined() {
		this._setMinCellTemperature(null);
		this.getMinCellTemperatureChannel().nextProcessImage();
	}

	public void setMaximalCellTemperature(int maximalCellTemperature) {
		this._setMaxCellTemperature(maximalCellTemperature);
		this.getMaxCellTemperatureChannel().nextProcessImage();
	}

	public void setMaximalCellTemperatureToUndefined() {
		this._setMaxCellTemperature(null);
		this.getMaxCellTemperatureChannel().nextProcessImage();
	}

	public void setSoc(int soc) {
		this._setSoc(soc);
		this.getSocChannel().nextProcessImage();
	}

	public void setSocToUndefined() {
		this._setSoc(null);
		this.getSocChannel().nextProcessImage();
	}

	public void setSoh(int soh) {
		this._setSoh(soh);
		this.getSohChannel().nextProcessImage();
	}

	public void setCapacity(int cap) {
		this._setCapacity(cap);
		this.getCapacityChannel().nextProcessImage();
	}

	public void setMaximalChargeCurrent(int max) {
		this._setChargeMaxCurrent(max);
		this.getChargeMaxCurrentChannel().nextProcessImage();
	}

	public void setMaximalChargeCurrentToUndefined() {
		this._setChargeMaxCurrent(null);
		this.getChargeMaxCurrentChannel().nextProcessImage();
	}

	public void setMaximalDischargeCurrent(int max) {
		this._setDischargeMaxCurrent(max);
		this.getDischargeMaxCurrentChannel().nextProcessImage();
	}

	public void setMaximalDischargeCurrentToUndefined() {
		this._setDischargeMaxCurrent(null);
		this.getDischargeMaxCurrentChannel().nextProcessImage();
	}

	public void setVoltage(int voltage) {
		this._setVoltage(voltage);
		this.getVoltageChannel().nextProcessImage();
	}

	public void setVoltageToUndefined() {
		this._setVoltage(null);
		this.getVoltageChannel().nextProcessImage();
	}

	public void setCurrent(int current) {
		this._setCurrent(current);
		this.getCurrentChannel().nextProcessImage();
	}

	@Override
	public void start() throws OpenemsNamedException {
		this.setStartStop(StartStop.START);
		this.getStartStopChannel().nextProcessImage();
	}

	@Override
	public void stop() throws OpenemsNamedException {
		this.setStartStop(StartStop.STOP);
		this.getStartStopChannel().nextProcessImage();
	}

	public void setChargeMaxVoltage(int voltage) {
		this._setChargeMaxVoltage(voltage);
		this.getChargeMaxVoltageChannel().nextProcessImage();
	}

	public void setDischargeMinVoltage(int voltage) {
		this._setDischargeMinVoltage(voltage);
		this.getDischargeMinVoltageChannel().nextProcessImage();
	}

	public float getMinimalCellVoltage() {
		return getMinCellVoltage().orElse(0);
	}

	public float getMaximalCellVoltage() {
		return getMaxCellVoltage().orElse(0);
	}

	public float getSoCX() {
		return getSoc().orElse(0);
	}

	public float getCapacityX() {
		return getCapacity().orElse(0);
	}

	public float getCurrentX() {
		return getCurrent().orElse(0);
	}

	public float getVoltageX() {
		return getVoltage().orElse(0);
	}

	public float getMaxChargeCurrentX() {
		return getChargeMaxCurrent().orElse(0);
	}

	public float getMaxDischargeCurrentX() {
		return getDischargeMaxCurrent().orElse(0);
	}

	public float getMaxChargeVoltageX() {
		return getChargeMaxVoltage().orElse(0);
	}

	public float getMinDischargeVoltageX() {
		return getDischargeMinVoltage().orElse(0);
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		_setStartStop(value);
	}
}

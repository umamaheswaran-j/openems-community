package io.openems.edge.controller.ess.timeofusetariff.discharge;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.emergencycapacityreserve.EmergencyCapacityReserve;
import io.openems.edge.controller.ess.limittotaldischarge.LimitTotalDischargeController;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.predictor.api.manager.PredictorManager;
import io.openems.edge.predictor.api.oneday.Prediction24Hours;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateActiveTime;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;
import io.openems.edge.timeofusetariff.api.utils.TimeOfUseTariffUtils;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.Time-Of-Use-Tariff.Discharge", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TimeOfUseTariffDischargeImpl extends AbstractOpenemsComponent
		implements Controller, OpenemsComponent, TimedataProvider, TimeOfUseTariffDischarge {

	private static final ChannelAddress SUM_PRODUCTION = new ChannelAddress("_sum", "ProductionActivePower");
	private static final ChannelAddress SUM_CONSUMPTION = new ChannelAddress("_sum", "ConsumptionActivePower");

	/**
	 * Delayed Time is aggregated also after restart of OpenEMS.
	 */
	private final CalculateActiveTime calculateDelayedTime = new CalculateActiveTime(this,
			TimeOfUseTariffDischarge.ChannelId.DELAYED_TIME);

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private PredictorManager predictorManager;

	@Reference
	private TimeOfUseTariff timeOfUseTariff;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
	private volatile List<EmergencyCapacityReserve> ctrlEmergencyCapacityReserves = new CopyOnWriteArrayList<>();

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
	private volatile List<LimitTotalDischargeController> ctrlLimitTotalDischargeControllers = new CopyOnWriteArrayList<>();

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private ManagedSymmetricEss ess;

	private Config config = null;
	private BoundarySpace boundarySpace = null;
	private TreeMap<ZonedDateTime, Integer> consumptionMap = new TreeMap<>();
	private TreeMap<ZonedDateTime, Integer> productionMap = new TreeMap<>();
	private List<ZonedDateTime> targetPeriods = new ArrayList<ZonedDateTime>();
	private TreeMap<ZonedDateTime, Float> quarterlyPricesMap = new TreeMap<>();
	private TreeMap<ZonedDateTime, Integer> socWithoutLogic = new TreeMap<>();
	private ZonedDateTime lastAccessedTime = ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
	private ZonedDateTime lastUpdatePriceTime = ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());

	public TimeOfUseTariffDischargeImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				TimeOfUseTariffDischarge.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

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

		// Current Date Time rounded off to 15 minutes.
		ZonedDateTime now = TimeOfUseTariffUtils.getNowRoundedDownToMinutes(this.componentManager.getClock(), 15);

		// Prices contains the price values and the time it is retrieved.
		TimeOfUsePrices prices = this.timeOfUseTariff.getPrices();
		this.calculateBoundarySpace(now, prices);

		// Mode given from the configuration.
		switch (this.config.mode()) {

		case AUTOMATIC:
			this.modeAutomatic(now);
			break;
		case OFF:
			this.modeOff();
			break;
		}

		this.updateVisualizationChannels(now);
	}

	/**
	 * calculates the boundary space for the activation of the controller.
	 * 
	 * @param now    Current Date Time rounded off to 15 minutes.
	 * @param prices TimeOfUsePrices object, containing prices and the time it
	 *               retrieved.
	 */
	private void calculateBoundarySpace(ZonedDateTime now, TimeOfUsePrices prices) {

		/*
		 * Every day, Prices are updated in API at a certain hour. we update the
		 * predictions and the prices during those hour.
		 * 
		 * gets the prices and predictions when the controller is restarted or //
		 * re-enabled in any time.
		 */
		if (prices != null && this.lastUpdatePriceTime.isBefore(prices.getUpdateTime())) {
			// gets the prices, predictions and calculates the boundary space.
			this.getBoundarySpace(now, prices);

			// update lastUpdateTimestamp
			this.lastUpdatePriceTime = prices.getUpdateTime();
		} else {
			// update the channel
			this.channel(TimeOfUseTariffDischarge.ChannelId.QUATERLY_PRICES_TAKEN).setNextValue(false);
		}
	}

	/**
	 * Calculate the Target Periods every 15 minutes within the boundary period.
	 * 
	 * @param now Current Date Time rounded off to 15 minutes.
	 * @throws InvalidValueException on error
	 */
	private void calculateTargetPeriodsWithinBoundarySpace(ZonedDateTime now) throws InvalidValueException {
		// Initializing with Default values.
		this._setTargetHoursCalculated(false);

		// if the boundary space are calculated, start scheduling only during boundary
		// space.
		if (this.boundarySpace != null && this.boundarySpace.isWithinBoundary(now)) {

			// Runs every 15 minutes.
			if (now.isAfter(this.lastAccessedTime)) {

				long availableEnergy = this.getAvailableEnergy(now);
				long remainingEnergy = this.getRemainingCapacity(availableEnergy, this.productionMap,
						this.consumptionMap, now, this.boundarySpace);

				// Resetting
				this.targetPeriods.clear();

				// list of periods calculation.
				if (remainingEnergy > 0) {
					// Initiating the calculation
					this.targetPeriods = this.calculateTargetPeriods(this.consumptionMap, this.quarterlyPricesMap,
							remainingEnergy, this.boundarySpace);
					this._setTargetHoursCalculated(true);
				}

				this.channel(TimeOfUseTariffDischarge.ChannelId.TARGET_HOURS).setNextValue(this.targetPeriods.size());

				this.lastAccessedTime = now;
			}
		} else {
			this._setStateMachine(StateMachine.STANDBY);
		}
	}

	/**
	 * Returns the available energy in the battery which is usable for consumption
	 * after adjusting the minimum SoC capacity.
	 * 
	 * @param now Current Date Time rounded off to 15 minutes.
	 * @return available energy in Watt-milliseconds[Wmsec].
	 * @throws InvalidValueException on error
	 */
	private long getAvailableEnergy(ZonedDateTime now) throws InvalidValueException {

		final int netCapacity = this.ess.getCapacity().getOrError();
		final int soc = this.ess.getSoc().getOrError();

		// Usable capacity based on minimum SoC from Limit total discharge and emergency
		// reserve controllers.
		int limitSoc = 0;
		for (LimitTotalDischargeController ctrl : this.ctrlLimitTotalDischargeControllers) {
			limitSoc = Math.max(limitSoc, ctrl.getMinSoc());
		}
		for (EmergencyCapacityReserve ctrl : this.ctrlEmergencyCapacityReserves) {
			limitSoc = Math.max(limitSoc, ctrl.getConfig().reserveSoc());
		}
		this.channel(TimeOfUseTariffDischarge.ChannelId.MIN_SOC).setNextValue(limitSoc);

		// Calculating available energy and usable energy [Wmsec] in the battery.
		long availableEnergy = (long) (((double) netCapacity /* [Wh] */ * 3600 /* [Wsec] */ * 1000 /* [Wmsec] */
				/ 100 /* [%] */) * soc /* [current SoC] */);

		// Value is divided by 3600 * 1000 to convert from [Wmsec] to [Wh].
		this.channel(TimeOfUseTariffDischarge.ChannelId.AVAILABLE_CAPACITY).setNextValue(availableEnergy / 3600000);

		long limitEnergy = (long) (((double) netCapacity /* [Wh] */ * 3600 /* [Wsec] */ * 1000 /* [Wmsec] */
				/ 100 /* [%] */) * limitSoc /* [current SoC] */);

		availableEnergy = Math.max(0, (availableEnergy - limitEnergy));

		// Value is divided by 3600 * 1000 to convert from [Wmsec] to [Wh].
		this.channel(TimeOfUseTariffDischarge.ChannelId.USABLE_CAPACITY).setNextValue(availableEnergy / 3600000);

		// To estimate the soc curve when controller logic is not applied
		if (now.equals(this.boundarySpace.proLessThanCon)) {
			this.socWithoutLogic = this.generateSocCurveWithoutLogic(netCapacity, availableEnergy, limitEnergy,
					this.consumptionMap, soc, now, this.boundarySpace);
		}

		return availableEnergy;
	}

	/**
	 * This method calculates the boundary space within the prediction hours.
	 * 
	 * @param now    current time.
	 * @param prices TimeOfUsePrices object, containing prices and the time it
	 *               retrieved.
	 */
	private void getBoundarySpace(ZonedDateTime now, TimeOfUsePrices prices) {

		// Predictions as Integer array in 15 minute intervals.
		final Integer[] predictionProduction = this.predictorManager.get24HoursPrediction(SUM_PRODUCTION) //
				.getValues();
		final Integer[] predictionConsumption = this.predictorManager.get24HoursPrediction(SUM_CONSUMPTION) //
				.getValues();

		// Prices as Float array in 15 minute intervals.
		final Float[] quarterlyPrices = prices.getValues();
		this.channel(TimeOfUseTariffDischarge.ChannelId.QUATERLY_PRICES_TAKEN).setNextValue(true);

		// Converts the given 15 minute integer array to a TreeMap values.
		this.convertDataStructure(predictionProduction, predictionConsumption, now, quarterlyPrices);

		if (this.quarterlyPricesMap.isEmpty()) {
			this.channel(TimeOfUseTariffDischarge.ChannelId.QUATERLY_PRICES_TAKEN).setNextValue(false);
		}

		// Buffer minutes to adjust sunrise based on the risk level.
		int bufferMinutes = this.config.delayDischargeRiskLevel().bufferMinutes;

		// calculates the boundary space, within which the controller needs to work.
		this.boundarySpace = BoundarySpace.from(now, this.productionMap, this.consumptionMap,
				this.config.maxStartHour(), this.config.maxEndHour(), bufferMinutes);

		// Update Channels
		this.channel(TimeOfUseTariffDischarge.ChannelId.PRO_MORE_THAN_CON_SET)
				.setNextValue(this.boundarySpace.proMoreThanCon.getHour());
		this.channel(TimeOfUseTariffDischarge.ChannelId.PRO_MORE_THAN_CON_ACTUAL)
				.setNextValue(this.boundarySpace.proMoreThanCon.plusMinutes(bufferMinutes).getHour());
		this.channel(TimeOfUseTariffDischarge.ChannelId.PRO_LESS_THAN_CON)
				.setNextValue(this.boundarySpace.proLessThanCon.getHour());
	}

	/**
	 * This method returns the map of 15 minutes soc curve values when no controller
	 * logic is applied.
	 * 
	 * @param netCapacity     Net Capacity of the battery.
	 * @param availableEnergy available energy in the battery.
	 * @param limitEnergy     energy restricted to used based on min soc.
	 * @param consumptionMap  map of predicted consumption values.
	 * @param soc             current SoC of the battery.
	 * @param now             current time.
	 * @param boundarySpace   the {@link BoundarySpace}
	 * 
	 * @return {@link TreeMap} with {@link ZonedDateTime} as key and SoC as value.
	 */
	private TreeMap<ZonedDateTime, Integer> generateSocCurveWithoutLogic(int netCapacity, long availableEnergy,
			long limitEnergy, TreeMap<ZonedDateTime, Integer> consumptionMap, int soc, ZonedDateTime now,
			BoundarySpace boundarySpace) {

		TreeMap<ZonedDateTime, Integer> socWithoutLogic = new TreeMap<>();

		// current values.
		socWithoutLogic.put(now, soc);

		for (Entry<ZonedDateTime, Integer> entry : consumptionMap.subMap(now, boundarySpace.proMoreThanCon)
				.entrySet()) {

			long duration = 15 * 60 * 1000;
			long currentConsumptionEnergy = entry.getValue() * duration;

			if (availableEnergy > limitEnergy) {
				availableEnergy -= currentConsumptionEnergy;
			}

			double calculatedSoc = availableEnergy //
					/ (netCapacity * 3600. /* [Wsec] */ * 1000 /* [Wmsec] */) //
					* 100 /* [SoC] */;

			if (calculatedSoc > 100) {
				soc = 100;
			} else if (calculatedSoc < 0) {
				soc = 0;
			} else {
				soc = (int) Math.round(calculatedSoc);
			}

			socWithoutLogic.put(entry.getKey().plusMinutes(15), soc);
		}

		return socWithoutLogic;
	}

	/**
	 * Apply the actual logic of avoiding to discharge the battery during target
	 * periods.
	 * 
	 * @param now Current Date Time rounded off to 15 minutes.
	 * @throws OpenemsNamedException on error
	 */
	private void modeAutomatic(ZonedDateTime now) throws OpenemsNamedException {

		this.calculateTargetPeriodsWithinBoundarySpace(now);

		this._setTargetHoursIsEmpty(this.targetPeriods.isEmpty());

		ZonedDateTime currentQuarterHour = TimeOfUseTariffUtils
				.getNowRoundedDownToMinutes(this.componentManager.getClock(), 15) //
				.withZoneSameInstant(ZoneId.systemDefault());

		if (this.boundarySpace != null && this.boundarySpace.isWithinBoundary(now)) {
			if (this.targetPeriods.contains(currentQuarterHour)) {
				// set result
				this.ess.setActivePowerLessOrEquals(0);
				this._setDelayed(true);
				this._setStateMachine(StateMachine.DELAYED);
			} else {
				this._setDelayed(false);
				this._setStateMachine(StateMachine.ALLOWS_DISCHARGE);
			}
		}
	}

	/**
	 * Apply the mode OFF logic.
	 */
	private void modeOff() {
		// Do Nothing
		this._setTargetHoursCalculated(false);
		this._setTargetHoursIsEmpty(true);
		this._setDelayed(false);
		this._setStateMachine(StateMachine.STANDBY);
	}

	/**
	 * This is only to visualize data for better debugging.
	 * 
	 * @param now Current Date Time rounded off to 15 minutes.
	 */
	private void updateVisualizationChannels(ZonedDateTime now) {
		// Update time counter with 'Delayed' of this run.
		this.calculateDelayedTime.update(this.getDelayedChannel().getNextValue().orElse(false));

		// Storing quarterly prices in channel for visualization in Grafana and UI.
		if (!this.quarterlyPricesMap.isEmpty()) {
			for (Entry<ZonedDateTime, Float> entry : this.quarterlyPricesMap.entrySet()) {
				if (now.isEqual(entry.getKey())) {
					this._setQuarterlyPrices(entry.getValue());
				}
			}
		}

		// Storing Production and Consumption in channel for visualization in Grafana.
		if (!this.productionMap.isEmpty()) {
			for (Entry<ZonedDateTime, Integer> entry : this.productionMap.entrySet()) {
				if (now.isEqual(entry.getKey())) {
					this._setPredictedProduction(entry.getValue());
					this._setPredictedConsumption(this.consumptionMap.get(entry.getKey()));
				}
			}
		}

		Integer predictedSocWithoutLogic = null;
		if (!this.socWithoutLogic.isEmpty()) {
			if (this.boundarySpace.isWithinBoundary(now)) {
				for (Entry<ZonedDateTime, Integer> entry : this.socWithoutLogic.entrySet()) {
					if (now.isEqual(entry.getKey())) {
						predictedSocWithoutLogic = entry.getValue();
					}
				}
			}
		} else {
			this.socWithoutLogic.clear();
		}
		this._setPredictedSocWithoutLogic(predictedSocWithoutLogic);
	}

	/**
	 * This method converts the 15 minute integer array values to a {@link TreeMap}
	 * format for ease in later calculations.
	 * 
	 * @param productionValues  list of 96 production values predicted, comprising
	 *                          for next 24 hours.
	 * @param consumptionValues list of 96 consumption values predicted, comprising
	 *                          for next 24 hours.
	 * @param startHour         start hour of the predictions.
	 * @param quarterlyPrices   list of 96 quarterly electricity prices, comprising
	 *                          for next 24 hours.
	 */
	private void convertDataStructure(Integer[] productionValues, Integer[] consumptionValues, ZonedDateTime startHour,
			Float[] quarterlyPrices) {
		this.productionMap.clear();
		this.consumptionMap.clear();
		this.quarterlyPricesMap.clear();

		for (int i = 0; i < Prediction24Hours.NUMBER_OF_VALUES; i++) {
			Integer production = productionValues[i];
			Integer consumption = consumptionValues[i];
			Float price = quarterlyPrices[i];
			ZonedDateTime time = startHour.plusMinutes(i * 15);

			if (production != null) {
				this.productionMap.put(time, production);
			}

			if (consumption != null) {
				this.consumptionMap.put(time, consumption);
			}

			if (price != null) {
				this.quarterlyPricesMap.put(time, price);
			}
		}
	}

	/**
	 * This Method Returns the remaining Capacity that needs to be consumed from the
	 * Grid.
	 * 
	 * @param availableEnergy Amount of energy available in the ess based on SoC.
	 * @param productionMap   predicted production data along with time in
	 *                        {@link TreeMap} format.
	 * @param consumptionMap  predicted consumption data along with time in
	 *                        {@link TreeMap} format.
	 * @param now             Current Date Time rounded off to 15 minutes.
	 * @param boundarySpace   the {@link BoundarySpace}
	 * @return remainingCapacity Amount of energy that should be covered from grid
	 *         for consumption in night.
	 */
	private long getRemainingCapacity(long availableEnergy, TreeMap<ZonedDateTime, Integer> productionMap,
			TreeMap<ZonedDateTime, Integer> consumptionMap, ZonedDateTime now, BoundarySpace boundarySpace) {

		long consumptionEnergy = 0;
		long remainingEnergy = 0;

		for (Entry<ZonedDateTime, Integer> entry : consumptionMap //
				.subMap(now, boundarySpace.proMoreThanCon) //
				.entrySet()) {

			long duration = 15 * 60 * 1000;
			long currentConsumptionEnergy = entry.getValue() * duration;
			long currentProductionEnergy = productionMap.get(entry.getKey()) * duration;

			consumptionEnergy = consumptionEnergy + currentConsumptionEnergy - Math.max(0, currentProductionEnergy);
		}

		// remaining amount of energy that should be covered from grid.
		remainingEnergy = Math.max(0, (consumptionEnergy - availableEnergy));

		// Update Channels
		// Values are divided by 3600 * 1000 to convert from [Wmsec] to [Wh].
		this.channel(TimeOfUseTariffDischarge.ChannelId.TOTAL_CONSUMPTION).setNextValue((consumptionEnergy / 3600000));
		this.channel(TimeOfUseTariffDischarge.ChannelId.REMAINING_CONSUMPTION)
				.setNextValue((remainingEnergy / 3600000));

		return remainingEnergy;
	}

	/**
	 * This method returns the list of periods, during which ESS is avoided for
	 * consumption.
	 * 
	 * @param consumptionMap  predicted consumption data along with time in
	 *                        {@link TreeMap} format.
	 * @param quarterlyPrices {@link TreeMap} consisting of hourly electricity
	 *                        prices along with time.
	 * @param remainingEnergy Amount of energy that should be covered from grid for
	 *                        consumption in night.
	 * @param boundarySpace   the {@link BoundarySpace}
	 * @return {@link List} list of target periods to avoid charging/discharging of
	 *         the battery.
	 */
	private List<ZonedDateTime> calculateTargetPeriods(TreeMap<ZonedDateTime, Integer> consumptionMap,
			TreeMap<ZonedDateTime, Float> quarterlyPrices, long remainingEnergy, BoundarySpace boundarySpace) {

		List<ZonedDateTime> targetHours = new ArrayList<ZonedDateTime>();
		ZonedDateTime currentQuarterHour = TimeOfUseTariffUtils
				.getNowRoundedDownToMinutes(this.componentManager.getClock(), 15) //
				.withZoneSameInstant(ZoneId.systemDefault());

		List<Entry<ZonedDateTime, Float>> priceList = new ArrayList<>(quarterlyPrices //
				.subMap(currentQuarterHour, boundarySpace.proMoreThanCon) //
				.entrySet());
		priceList.sort(Entry.comparingByValue());
		long duration = 15 * 60 * 1000;

		for (Entry<ZonedDateTime, Float> entry : priceList) {
			targetHours.add(entry.getKey());

			remainingEnergy = remainingEnergy - (consumptionMap.get(entry.getKey()) * duration);

			// checks if we have sufficient capacity.
			if (remainingEnergy <= 0) {
				break;
			}
		}

		return targetHours;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}
}

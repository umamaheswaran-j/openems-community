package io.openems.edge.timeofusetariff.api.utils;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import com.google.common.collect.ImmutableSortedMap;

import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

public class TimeOfUseTariffUtils {

	/**
	 * Returns the Array of 24 hour [96 quarterly] electricity prices in EUR/MWh.
	 * 
	 * @param clock           the {@link Clock}
	 * @param priceMap        {@link ImmutableSortedMap} with quarterly Time stamps
	 *                        and the price.
	 * @param updateTimeStamp time when prices are retrieved.
	 * @return the quarterly prices of next 24 hours along with the time they are
	 *         retrieved.
	 */
	public static TimeOfUsePrices getNext24HourPrices(Clock clock, ImmutableSortedMap<ZonedDateTime, Float> priceMap,
			ZonedDateTime updateTimeStamp) {

		// Returns the empty array if the map is empty.
		if (priceMap.isEmpty()) {
			return new TimeOfUsePrices(updateTimeStamp);
		}

		ZonedDateTime now = getNowRoundedDownToMinutes(clock, 15);
		// Converts the map values to array.
		// if the map size is less than 96, rest of the values will store as null.
		final Float[] priceList = priceMap.tailMap(now).values().toArray(new Float[TimeOfUsePrices.NUMBER_OF_VALUES]);

		return new TimeOfUsePrices(updateTimeStamp, priceList);
	}

	/**
	 * Gets 'now' from the Clock and rounds it down to required minutes.
	 * 
	 * @param clock   the {@link Clock}
	 * @param minutes the custom minutes to roundoff to.
	 * @return the rounded result
	 */
	public static ZonedDateTime getNowRoundedDownToMinutes(Clock clock, int minutes) {
		ZonedDateTime d = ZonedDateTime.now(clock);
		int minuteOfDay = d.get(ChronoField.MINUTE_OF_DAY);
		return d.with(ChronoField.NANO_OF_DAY, 0).plus(minuteOfDay / minutes * minutes, ChronoUnit.MINUTES);
	}
}

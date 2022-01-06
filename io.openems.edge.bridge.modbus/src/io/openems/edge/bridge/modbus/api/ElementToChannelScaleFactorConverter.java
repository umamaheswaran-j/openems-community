package io.openems.edge.bridge.modbus.api;

import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.sunspec.SunSpecPoint;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Converts between Element and Channel by applying a scale factor.
 * 
 * <p>
 * (channel = element * 10^scaleFactor)
 * 
 * <p>
 * Example: if the Register is in unit [0.1 V] and this converter has a
 * scaleFactor of '2', it converts to unit [1 mV]
 */
public class ElementToChannelScaleFactorConverter extends ElementToChannelConverter {

	public ElementToChannelScaleFactorConverter(OpenemsComponent component, SunSpecPoint point,
			ChannelId scaleFactorChannel) {
		super(//
				// element -> channel
				value -> {
					if (!point.isDefined(value)) {
						return null;
					}
					try {
						return apply(value,
								((IntegerReadChannel) component.channel(scaleFactorChannel)).value().getOrError() * -1);
					} catch (InvalidValueException | IllegalArgumentException e) {
						return null;
					}
				}, //

				// channel -> element
				value -> {
					try {
						return apply(value,
								((IntegerReadChannel) component.channel(scaleFactorChannel)).value().getOrError());
					} catch (InvalidValueException | IllegalArgumentException e) {
						return null;
					}
				});
	}

	public ElementToChannelScaleFactorConverter(int scaleFactor) {
		super(//
				// element -> channel
				value -> {
					return apply(value, scaleFactor * -1);
				}, //

				// channel -> element
				value -> {
					return apply(value, scaleFactor);
				});
	}

	private static Object apply(Object value, int scaleFactor) {
		double factor = Math.pow(10, scaleFactor * -1);
		if (value == null) {
			return null;
		}
		for (OpenemsType openemsType : OpenemsType.values()) {
			// this 'for' + 'switch' is only utilized to get an alert by Eclipse IDE if a
			// new OpenemsType was added. ("The enum constant [...] needs a corresponding
			// case label in this enum switch on OpenemsType")
			switch (openemsType) {
			case BOOLEAN:
			case SHORT:
			case INTEGER:
			case LONG:
			case FLOAT:
			case DOUBLE:
			case STRING:
				if (value instanceof Boolean) {
					return (boolean) value;
				}
				if (value instanceof Short) {
					double result = ((Short) value) * factor;
					if (result >= Short.MIN_VALUE && result <= Short.MAX_VALUE) {
						return Short.valueOf((short) result);
					} else if (result > Integer.MIN_VALUE && result < Integer.MAX_VALUE) {
						return Integer.valueOf((int) result);
					} else {
						return Double.valueOf(Math.round(result));
					}
				}
				if (value instanceof Integer) {
					double result = ((Integer) value) * factor;
					if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
						return Integer.valueOf((int) result);
					} else {
						return Double.valueOf(Math.round(result));
					}
				}
				if (value instanceof Long) {
					double result = ((Long) value) * factor;
					return Math.round(result);
				}
				if (value instanceof Float) {
					double result = ((Float) value) * factor;
					if (result >= Float.MIN_VALUE && result <= Float.MAX_VALUE) {
						return Float.valueOf((float) result);
					} else {
						return Double.valueOf(result);
					}
				}
				if (value instanceof Double) {
					return Double.valueOf(((Double) value) * factor);
				}
				if (value instanceof String) {
					return (String) value;
				}
			}
			break;
		}
		throw new IllegalArgumentException(
				"Type [" + value.getClass().getName() + "] not supported by SCALE_FACTOR converter");
	}
}

package io.openems.edge.ess.cluster;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;

public class ChannelManager extends AbstractChannelListenerManager {

	private final EssClusterImpl parent;

	public ChannelManager(EssClusterImpl parent) {
		this.parent = parent;
	}

	/**
	 * Called on Component activate().
	 * 
	 * @param esss the List of {@link SymmetricEss}
	 */
	protected void activate(List<SymmetricEss> esss) {
		// SymmetricEss
		this.calculateSoc(esss);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.CAPACITY);
		this.calculateGridMode(esss);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.ACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.REACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, SymmetricEss.ChannelId.MAX_APPARENT_POWER);

		this.calculate(LONG_SUM, esss, SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
		this.calculate(LONG_SUM, esss, SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);

		this.calculate(INTEGER_MIN, esss, SymmetricEss.ChannelId.MIN_CELL_VOLTAGE);
		this.calculate(INTEGER_MAX, esss, SymmetricEss.ChannelId.MAX_CELL_VOLTAGE);
		this.calculate(INTEGER_MIN, esss, SymmetricEss.ChannelId.MIN_CELL_TEMPERATURE);
		this.calculate(INTEGER_MAX, esss, SymmetricEss.ChannelId.MAX_CELL_TEMPERATURE);

		// AsymmetricEss
		this.calculate(INTEGER_SUM, esss, AsymmetricEss.ChannelId.ACTIVE_POWER_L1, //
				DIVIDE_BY_THREE, SymmetricEss.ChannelId.ACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, AsymmetricEss.ChannelId.ACTIVE_POWER_L2, //
				DIVIDE_BY_THREE, SymmetricEss.ChannelId.ACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, AsymmetricEss.ChannelId.ACTIVE_POWER_L3, //
				DIVIDE_BY_THREE, SymmetricEss.ChannelId.ACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, AsymmetricEss.ChannelId.REACTIVE_POWER_L1, //
				DIVIDE_BY_THREE, SymmetricEss.ChannelId.REACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, AsymmetricEss.ChannelId.REACTIVE_POWER_L2, //
				DIVIDE_BY_THREE, SymmetricEss.ChannelId.REACTIVE_POWER);
		this.calculate(INTEGER_SUM, esss, AsymmetricEss.ChannelId.REACTIVE_POWER_L3, //
				DIVIDE_BY_THREE, SymmetricEss.ChannelId.REACTIVE_POWER);

		// ManagedSymmetricEss
		this.calculate(INTEGER_SUM, esss, ManagedSymmetricEss.ChannelId.ALLOWED_CHARGE_POWER);
		this.calculate(INTEGER_SUM, esss, ManagedSymmetricEss.ChannelId.ALLOWED_DISCHARGE_POWER);
	}

	/**
	 * Calculate effective Grid-Mode of {@link SymmetricEss}.
	 * 
	 * @param esss the List of {@link SymmetricEss}
	 */
	private void calculateGridMode(List<SymmetricEss> esss) {
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			int onGrids = 0;
			int offGrids = 0;
			for (SymmetricEss ess : esss) {
				switch (ess.getGridMode()) {
				case OFF_GRID:
					offGrids++;
					break;
				case ON_GRID:
					onGrids++;
					break;
				case UNDEFINED:
					break;
				}
			}

			final GridMode result;
			if (esss.size() == onGrids) {
				result = GridMode.ON_GRID;
			} else if (esss.size() == offGrids) {
				result = GridMode.OFF_GRID;
			} else {
				result = GridMode.UNDEFINED;
			}
			this.parent._setGridMode(result);
		};

		for (SymmetricEss ess : esss) {
			this.addOnChangeListener(ess, SymmetricEss.ChannelId.GRID_MODE, callback);
		}
	}

	/**
	 * Calculate weighted State-Of-Charge of {@link SymmetricEss}.
	 * 
	 * @param esss the List of {@link SymmetricEss}
	 */
	private void calculateSoc(List<SymmetricEss> esss) {
		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
			Integer socCapacity = null;
			Integer totalCapacity = null;
			for (SymmetricEss ess : esss) {
				Value<Integer> capacity = ess.getCapacity();
				Value<Integer> soc = ess.getSoc();
				if (!capacity.isDefined() || !soc.isDefined()) {
					continue;
				}
				socCapacity = TypeUtils.sum(socCapacity, soc.get() * capacity.get());
				totalCapacity = TypeUtils.sum(totalCapacity, capacity.get());
			}

			if (socCapacity != null && totalCapacity != null) {
				this.parent._setSoc(Math.round(socCapacity / Float.valueOf(totalCapacity)));
			}
		};

		this.addOnChangeListener(this.parent, SymmetricEss.ChannelId.CAPACITY, callback);
		for (SymmetricEss ess : esss) {
			this.addOnChangeListener(ess, SymmetricEss.ChannelId.SOC, callback);
		}
	}

	private final static Function<Integer, Integer> DIVIDE_BY_THREE = (value) -> {
		return TypeUtils.divide(value, 3);
	};
	private final static BiFunction<Integer, Integer, Integer> INTEGER_MIN = (result, value) -> {
		return TypeUtils.min(result, value);
	};

	private final static BiFunction<Integer, Integer, Integer> INTEGER_MAX = (result, value) -> {
		return TypeUtils.max(result, value);
	};

	private final static BiFunction<Integer, Integer, Integer> INTEGER_SUM = (result, value) -> {
		return TypeUtils.sum(result, value);
	};

	private final static BiFunction<Long, Long, Long> LONG_SUM = (result, value) -> {
		return TypeUtils.sum(result, value);
	};

	/**
	 * Aggregate Channels of {@link SymmetricEss}s.
	 * 
	 * @param aggregator the aggregator function
	 * @param esss       the List of {@link SymmetricEss}
	 * @param channelId  the SymmetricEss.ChannelId
	 */
	private <T> void calculate(BiFunction<T, T, T> aggregator, List<SymmetricEss> esss,
			SymmetricEss.ChannelId channelId) {
		final BiConsumer<Value<T>, Value<T>> callback = (oldValue, newValue) -> {
			T result = null;
			for (SymmetricEss ess : esss) {
				Channel<T> channel = ess.channel(channelId);
				result = aggregator.apply(result, channel.getNextValue().get());
			}

			Channel<T> channel = this.parent.channel(channelId);
			channel.setNextValue(result);
		};

		for (SymmetricEss ess : esss) {
			this.addOnChangeListener(ess, channelId, callback);
		}
	}

	/**
	 * Aggregate Channels of {@link ManagedSymmetricEss}s.
	 * 
	 * @param aggregator the aggregator function
	 * @param esss       the List of {@link SymmetricEss}
	 * @param channelId  the SymmetricEss.ChannelId
	 */
	private <T> void calculate(BiFunction<T, T, T> aggregator, List<SymmetricEss> esss,
			ManagedSymmetricEss.ChannelId channelId) {
		final BiConsumer<Value<T>, Value<T>> callback = (oldValue, newValue) -> {
			T result = null;
			for (SymmetricEss ess : esss) {
				if (ess instanceof ManagedSymmetricEss) {
					Channel<T> channel = ((ManagedSymmetricEss) ess).channel(channelId);
					result = aggregator.apply(result, channel.getNextValue().get());
				}
			}

			Channel<T> channel = this.parent.channel(channelId);
			channel.setNextValue(result);
		};

		for (SymmetricEss ess : esss) {
			if (ess instanceof ManagedSymmetricEss) {
				this.addOnChangeListener((ManagedSymmetricEss) ess, channelId, callback);
			}
		}
	}

	/**
	 * Aggregate Channels of {@link AsymmetricEss}s.
	 * 
	 * @param aggregator          the aggregator function
	 * @param esss                the List of {@link SymmetricEss}
	 * @param asymmetricChannelId the AsymmetricEss.ChannelId
	 * @param asymmetricChannelId the fallback SymmetricEss.ChannelId; used for
	 *                            SymmetricEss and divided by 3
	 */
	private <T> void calculate(BiFunction<T, T, T> aggregator, List<SymmetricEss> esss,
			AsymmetricEss.ChannelId asymmetricChannelId, Function<T, T> divideFunction,
			SymmetricEss.ChannelId symmetricChannelId) {
		final BiConsumer<Value<T>, Value<T>> callback = (oldValue, newValue) -> {
			T result = null;
			for (SymmetricEss ess : esss) {
				if (ess instanceof AsymmetricEss) {
					Channel<T> channel = ((AsymmetricEss) ess).channel(asymmetricChannelId);
					result = aggregator.apply(result, channel.getNextValue().get());
				} else {
					// SymmetricEss
					Channel<T> channel = ess.channel(symmetricChannelId);
					result = aggregator.apply(result, divideFunction.apply(channel.getNextValue().get()));
				}
			}

			Channel<Integer> channel = this.parent.channel(asymmetricChannelId);
			channel.setNextValue(result);
		};

		for (SymmetricEss ess : esss) {
			if (ess instanceof AsymmetricEss) {
				this.addOnChangeListener((AsymmetricEss) ess, asymmetricChannelId, callback);
			} else {
				this.addOnChangeListener(ess, symmetricChannelId, callback);
			}
		}
	}
//	/**
//	 * Aggregate Channels of {@link AsymmetricEss}s.
//	 * 
//	 * @param aggregator          the aggregator function
//	 * @param esss                the List of {@link SymmetricEss}
//	 * @param asymmetricChannelId the AsymmetricEss.ChannelId
//	 * @param asymmetricChannelId the fallback SymmetricEss.ChannelId; used for
//	 *                            SymmetricEss and divided by 3
//	 */
//	private void calculate(BiFunction<Integer, Integer, Integer> aggregator, List<SymmetricEss> esss,
//			AsymmetricEss.ChannelId asymmetricChannelId, SymmetricEss.ChannelId symmetricChannelId) {
//		final BiConsumer<Value<Integer>, Value<Integer>> callback = (oldValue, newValue) -> {
//			Integer result = null;
//			for (SymmetricEss ess : esss) {
//				if (ess instanceof AsymmetricEss) {
//					Channel<Integer> channel = ((AsymmetricEss) ess).channel(asymmetricChannelId);
//					result = aggregator.apply(result, channel.getNextValue().get());
//				} else {
//					// SymmetricEss
//					Channel<Integer> channel = ess.channel(symmetricChannelId);
//					result = aggregator.apply(result, TypeUtils.divide(channel.getNextValue().get(), 3));
//				}
//			}
//			
//			Channel<Integer> channel = this.parent.channel(asymmetricChannelId);
//			channel.setNextValue(result);
//		};
//		
//		for (SymmetricEss ess : esss) {
//			if (ess instanceof AsymmetricEss) {
//				this.addOnChangeListener((AsymmetricEss) ess, asymmetricChannelId, callback);
//			} else {
//				this.addOnChangeListener(ess, symmetricChannelId, callback);
//			}
//		}
//	}

}

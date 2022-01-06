package io.openems.edge.fenecon.dess.ess;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.fenecon.dess.charger.AbstractFeneconDessCharger;

public interface FeneconDessEss extends AsymmetricEss, SymmetricEss, OpenemsComponent {

	public static final int MAX_APPARENT_POWER = 9_000; // [VA]
	public static final int CAPACITY = 10_000; // [Wh]

	/**
	 * Gets the Modbus Unit-ID.
	 * 
	 * @return the Unit-ID
	 */
	public Integer getUnitId();

	/**
	 * Gets the Modbus-Bridge Component-ID, i.e. "modbus0".
	 * 
	 * @return the Component-ID
	 */
	public String getModbusBridgeId();

	/**
	 * Registers a Charger with this ESS.
	 * 
	 * @param charger the Charger
	 */
	public void addCharger(AbstractFeneconDessCharger charger);

	/**
	 * Unregisters a Charger from this ESS.
	 * 
	 * @param charger the Charger
	 */
	public void removeCharger(AbstractFeneconDessCharger charger);

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		SYSTEM_STATE(Doc.of(SystemState.values())), //
		ORIGINAL_ACTIVE_CHARGE_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.WATT_HOURS)),
		ORIGINAL_ACTIVE_DISCHARGE_ENERGY(Doc.of(OpenemsType.LONG) //
				.unit(Unit.WATT_HOURS)),
		BSMU_WORK_STATE(Doc.of(BsmuWorkState.values()) //
				.onInit(channel -> { //
					// on each update set Grid-Mode channel
					((Channel<Integer>) channel).onChange((oldValue, newValue) -> {
						BsmuWorkState state = newValue.asEnum();
						SymmetricEss parent = (SymmetricEss) channel.getComponent();
						switch (state) {
						case ON_GRID:
							parent._setGridMode(GridMode.ON_GRID);
							break;
						case OFF_GRID:
							parent._setGridMode(GridMode.OFF_GRID);
							break;
						case FAULT:
						case UNDEFINED:
						case BEING_ON_GRID:
						case BEING_PRE_CHARGE:
						case BEING_STOP:
						case DEBUG:
						case INIT:
						case LOW_CONSUMPTION:
						case PRE_CHARGE:
							parent._setGridMode(GridMode.UNDEFINED);
							break;
						}
					});
				})), //
		STACK_CHARGE_STATE(Doc.of(StackChargeState.values())); //

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
}
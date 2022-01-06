package io.openems.backend.metadata.odoo;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Field {

	public String id();

	public int index();

	public String name();

	public boolean isQuery();

	/**
	 * Gets all fields that should be queried as a comma separated string.
	 * 
	 * @return the String
	 */
	public static String getSqlQueryFields(Field[] fields) {
		return Stream.of(fields) //
				.filter(f -> f.isQuery()) //
				.map(f -> f.id()) //
				.collect(Collectors.joining(","));
	}

	/**
	 * The EdgeDevice-Model.
	 */
	public enum EdgeDevice implements Field {
		ID("id", true), //
		APIKEY("apikey", true), //
		SETUP_PASSWORD("setup_password", true), //
		NAME("name", true), //
		COMMENT("comment", true), //
		STATE("state", true), //
		OPENEMS_VERSION("openems_version", true), //
		PRODUCT_TYPE("producttype", true), //
		OPENEMS_CONFIG("openems_config", true), //
		OPENEMS_CONFIG_COMPONENTS("openems_config_components", false), //
		LAST_MESSAGE("lastmessage", false), //
		LAST_UPDATE("lastupdate", false), //
		OPENEMS_SUM_STATE("openems_sum_state_level", true), //
		OPENEMS_IS_CONNECTED("openems_is_connected", false);

		public static final String ODOO_MODEL = "openems.edge";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;
		/**
		 * Holds information if this Field should be queried from Database.
		 */
		private final boolean query;

		private EdgeDevice(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		public String id() {
			return this.id;
		}

		public int index() {
			return this.queryIndex;
		}

		public boolean isQuery() {
			return this.query;
		}

	}

	/**
	 * The EdgeDeviceStatus-Model.
	 */
	public enum EdgeDeviceStatus implements Field {
		DEVICE_ID("edge_id", false), //
		CHANNEL_ADDRESS("channel_address", false), //
		LEVEL("level", true), //
		COMPONENT_ID("component_id", true), //
		CHANNEL_NAME("channel_name", true), //
		LAST_APPEARANCE("last_appearance", false), //
		LAST_ACKNOWLEDGE("last_acknowledge", false), //
		ACKNOWLEDGE_DAYS("acknowledge_days", false);

		public static final String ODOO_MODEL = "openems.edge_status";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;
		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private EdgeDeviceStatus(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		public String id() {
			return this.id;
		}

		public int index() {
			return this.queryIndex;
		}

		public boolean isQuery() {
			return this.query;
		}

	}

	/**
	 * The EdgeConfigUpdate-Model.
	 */
	public enum EdgeConfigUpdate implements Field {
		DEVICE_ID("edge_id", false), //
		TEASER("teaser", false), //
		DETAILS("details", false);

		public static final String ODOO_MODEL = "openems.openemsconfigupdate";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;
		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private EdgeConfigUpdate(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		public String id() {
			return this.id;
		}

		public int index() {
			return this.queryIndex;
		}

		public boolean isQuery() {
			return this.query;
		}

	}

	/**
	 * The EdgeDeviceUserRole-Model.
	 */
	public enum EdgeDeviceUserRole implements Field {
		DEVICE_ID("edge_id", false), //
		USER_ID("user_id", false), //
		ROLE("role", false);

		public static final String ODOO_MODEL = "openems.edge_user_role";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private EdgeDeviceUserRole(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}

	}

	public enum User implements Field {
		LOGIN("login", true), //
		PASSWORD("password", true), //
		PARTNER("partner_id", true), //
		GLOBAL_ROLE("global_role", true), //
		GROUPS("groups_id", true), //
		OPENEMS_LANGUAGE("openems_language", true);

		public static final String ODOO_MODEL = "res.users";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private User(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}

	}

	public enum Partner implements Field {
		FIRSTNAME("firstname", true), //
		LASTNAME("lastname", true), //
		EMAIL("email", true), //
		PHONE("phone", true), //
		COMPANY_NAME("commercial_company_name", true), //
		NAME("name", true), //
		IS_COMPANY("is_company", true), //
		PARENT("parent_id", true), //
		STREET("street", true), //
		ZIP("zip", true), //
		CITY("city", true), //
		COUNTRY("country_id", true), //
		ADDRESS_TYPE("type", true), //
		LANGUAGE("lang", true);

		public static final String ODOO_MODEL = "res.partner";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private Partner(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}

	}

	public enum Country implements Field {
		NAME("name", true), //
		CODE("code", true);

		public static final String ODOO_MODEL = "res.country";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private Country(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}
	}

	public enum SetupProtocol implements Field {
		CUSTOMER("customer_id", true), //
		DIFFERENT_LOCATION("different_location_id", true), //
		INSTALLER("installer_id", true), //
		EDGE("edge_id", true);

		public static final String ODOO_MODEL = "openems.setup_protocol";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private SetupProtocol(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}
	}

	public enum SetupProtocolProductionLot implements Field {
		SETUP_PROTOCOL("setup_protocol_id", true), //
		SEQUENCE("sequence", true), //
		LOT("lot_id", true);

		public static final String ODOO_MODEL = "openems.setup_protocol_production_lot";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private SetupProtocolProductionLot(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}
	}

	public enum SetupProtocolItem implements Field {
		SETUP_PROTOCOL("setup_protocol_id", true), //
		SEQUENCE("sequence", true);

		public static final String ODOO_MODEL = "openems.setup_protocol_item";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private SetupProtocolItem(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}
	}

	public enum StockProductionLot implements Field {
		SERIAL_NUMBER("name", true), //
		PRODUCT("product_id", true);

		public static final String ODOO_MODEL = "stock.production.lot";
		public static final String ODOO_TABLE = ODOO_MODEL.replace(".", "_");

		private static final class StaticFields {
			private static int nextQueryIndex = 1;
		}

		private final int queryIndex;
		private final String id;

		/**
		 * Holds information if this Field should be queried from and written to
		 * Database.
		 */
		private final boolean query;

		private StockProductionLot(String id, boolean query) {
			this.id = id;
			this.query = query;
			if (query) {
				this.queryIndex = StaticFields.nextQueryIndex++;
			} else {
				this.queryIndex = -1;
			}
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public int index() {
			return this.queryIndex;
		}

		@Override
		public boolean isQuery() {
			return this.query;
		}
	}

}

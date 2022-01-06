package io.openems.edge.bosch.bpts5hybrid.core;

import io.openems.edge.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	public static class Builder {
		private String id = null;
		private String modbusId = null;
		public String ipaddress;
		public int interval;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setIpaddress(String ipaddress) {
			this.ipaddress = ipaddress;
			return this;
		}

		public Builder setInterval(int interval) {
			this.interval = interval;
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
	public String ipaddress() {
		return this.builder.ipaddress;
	}

	@Override
	public int interval() {
		return this.builder.interval;
	}

}
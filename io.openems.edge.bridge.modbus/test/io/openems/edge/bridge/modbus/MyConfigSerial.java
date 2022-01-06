package io.openems.edge.bridge.modbus;

import io.openems.edge.bridge.modbus.api.LogVerbosity;
import io.openems.edge.bridge.modbus.api.Parity;
import io.openems.edge.bridge.modbus.api.Stopbit;
import io.openems.edge.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfigSerial extends AbstractComponentConfig implements ConfigSerial {

	protected static class Builder {
		private String id = null;
		public String portName;
		public int baudRate;
		public int databits;
		public Stopbit stopbits;
		public Parity parity;
		public LogVerbosity logVerbosity;
		public int invalidateElementsAfterReadErrors;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setPortName(String portName) {
			this.portName = portName;
			return this;
		}

		public Builder setBaudRate(int baudRate) {
			this.baudRate = baudRate;
			return this;
		}

		public Builder setDatabits(int databits) {
			this.databits = databits;
			return this;
		}

		public Builder setStopbits(Stopbit stopbits) {
			this.stopbits = stopbits;
			return this;
		}

		public Builder setParity(Parity parity) {
			this.parity = parity;
			return this;
		}

		public Builder setLogVerbosity(LogVerbosity logVerbosity) {
			this.logVerbosity = logVerbosity;
			return this;
		}

		public Builder setInvalidateElementsAfterReadErrors(int invalidateElementsAfterReadErrors) {
			this.invalidateElementsAfterReadErrors = invalidateElementsAfterReadErrors;
			return this;
		}

		public MyConfigSerial build() {
			return new MyConfigSerial(this);
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

	private MyConfigSerial(Builder builder) {
		super(ConfigSerial.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String portName() {
		return this.builder.portName;
	}

	@Override
	public int baudRate() {
		return this.builder.baudRate;
	}

	@Override
	public int databits() {
		return this.builder.databits;
	}

	@Override
	public Stopbit stopbits() {
		return this.builder.stopbits;
	}

	@Override
	public Parity parity() {
		return this.builder.parity;
	}

	@Override
	public LogVerbosity logVerbosity() {
		return this.builder.logVerbosity;
	}

	@Override
	public int invalidateElementsAfterReadErrors() {
		return this.builder.invalidateElementsAfterReadErrors;
	}

}
package io.openems.edge.bridge.modbus;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.Designate;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.util.SerialParameters;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractModbusBridge;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.BridgeModbusSerial;
import io.openems.edge.bridge.modbus.api.Parity;
import io.openems.edge.bridge.modbus.api.Stopbit;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.event.EdgeEventConstants;

/**
 * Provides a service for connecting to, querying and writing to a Modbus/RTU
 * device.
 */
@Designate(ocd = ConfigSerial.class, factory = true)
@Component(name = "Bridge.Modbus.Serial", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, //
				EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE //
		})
public class BridgeModbusSerialImpl extends AbstractModbusBridge
		implements BridgeModbus, BridgeModbusSerial, OpenemsComponent, EventHandler {

	// private final Logger log =
	// LoggerFactory.getLogger(BridgeModbusSerialImpl.class);

	@Reference
	private Cycle cycle;

	/**
	 * The configured Port-Name (e.g. '/dev/ttyUSB0' or 'COM3').
	 */
	private String portName = "";

	/**
	 * The configured Baudrate (e.g. 9600).
	 */
	private int baudrate;

	/**
	 * The configured Databits (e.g. 8).
	 */
	private int databits;

	/**
	 * The configured Stopbits.
	 */
	private Stopbit stopbits;

	/**
	 * The configured parity.
	 */
	private Parity parity;

	public BridgeModbusSerialImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				BridgeModbus.ChannelId.values(), //
				BridgeModbusSerial.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, ConfigSerial config) {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.logVerbosity(),
				config.invalidateElementsAfterReadErrors());
		this.portName = config.portName();
		this.baudrate = config.baudRate();
		this.databits = config.databits();
		this.stopbits = config.stopbits();
		this.parity = config.parity();
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected Cycle getCycle() {
		return this.cycle;
	}

	@Override
	public void closeModbusConnection() {
		if (this._connection != null) {
			this._connection.close();
			this._connection = null;
		}
	}

	@Override
	public ModbusTransaction getNewModbusTransaction() throws OpenemsException {
		SerialConnection connection = this.getModbusConnection();
		ModbusSerialTransaction transaction = new ModbusSerialTransaction(connection);
		transaction.setRetries(AbstractModbusBridge.DEFAULT_RETRIES);
		return transaction;
	}

	private SerialConnection _connection = null;

	private synchronized SerialConnection getModbusConnection() throws OpenemsException {
		if (this._connection == null) {
			/*
			 * create new connection
			 */
			SerialParameters params = new SerialParameters();
			params.setPortName(this.portName);
			params.setBaudRate(this.baudrate);
			params.setDatabits(this.databits);
			params.setStopbits(this.stopbits.getValue());
			params.setParity(this.parity.getValue());
			params.setEncoding(Modbus.SERIAL_ENCODING_RTU);
			params.setEcho(false);
			SerialConnection connection = new SerialConnection(params);
			this._connection = connection;
		}
		if (!this._connection.isOpen()) {
			try {
				this._connection.open();
			} catch (Exception e) {
				throw new OpenemsException("Connection via [" + this.portName + "] failed: " + e.getMessage());
			}
			this._connection.getModbusTransport().setTimeout(AbstractModbusBridge.DEFAULT_TIMEOUT);
		}
		return this._connection;
	}

	@Override
	public int getBaudrate() {
		return this.baudrate;
	}

	@Override
	public int getDatabits() {
		return this.databits;
	}

	@Override
	public Parity getParity() {
		return this.parity;
	}

	@Override
	public String getPortName() {
		return this.portName;
	}

	@Override
	public Stopbit getStopbits() {
		return this.stopbits;
	}
}
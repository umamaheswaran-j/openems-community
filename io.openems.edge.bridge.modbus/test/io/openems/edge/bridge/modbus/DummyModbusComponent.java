package io.openems.edge.bridge.modbus;

import org.osgi.framework.Constants;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.bridge.modbus.api.AbstractModbusBridge;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.DummyComponentContext;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.DummyConfigurationAdmin.DummyConfiguration;

public abstract class DummyModbusComponent extends AbstractOpenemsModbusComponent implements ModbusComponent {

	public DummyModbusComponent(String id, AbstractModbusBridge bridge, int unitId,
			io.openems.edge.common.channel.ChannelId[] additionalChannelIds) throws OpenemsException {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				additionalChannelIds //
		);
		for (Channel<?> channel : this.channels()) {
			channel.nextProcessImage();
		}
		this.setModbus(bridge);
		DummyComponentContext context = new DummyComponentContext();
		context.addProperty(Constants.SERVICE_PID, Constants.SERVICE_PID);
		DummyConfigurationAdmin cm = new DummyConfigurationAdmin();
		DummyConfiguration dummyConfiguration = new DummyConfiguration();
		dummyConfiguration.addProperty("Modbus.target",
				ConfigUtils.generateReferenceTargetFilter(Constants.SERVICE_PID, bridge.id()));
		cm.addConfiguration(Constants.SERVICE_PID, dummyConfiguration);
		super.activate(context, id, "", true, unitId, cm, "Modbus", bridge.id());
	}

	@Override
	protected abstract ModbusProtocol defineModbusProtocol() throws OpenemsException;

}

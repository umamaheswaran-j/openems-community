package io.openems.edge.wago;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbusTcp;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.AbstractModbusElement;
import io.openems.edge.bridge.modbus.api.element.CoilElement;
import io.openems.edge.bridge.modbus.api.element.ModbusCoilElement;
import io.openems.edge.bridge.modbus.api.task.FC1ReadCoilsTask;
import io.openems.edge.bridge.modbus.api.task.FC5WriteCoilTask;
import io.openems.edge.common.channel.BooleanReadChannel;
import io.openems.edge.common.channel.BooleanWriteChannel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.io.api.DigitalInput;
import io.openems.edge.io.api.DigitalOutput;

@Designate(ocd = Config.class, factory = true)
@Component(name = "IO.WAGO", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class Wago extends AbstractOpenemsModbusComponent
		implements DigitalOutput, DigitalInput, ModbusComponent, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(Wago.class);

	private static final int UNIT_ID = 1;

	@Reference
	protected ConfigurationAdmin cm;

	private InetAddress ipAddress = null;
	protected ModbusProtocol protocol = null;
	private CopyOnWriteArrayList<FieldbusModule> modules = new CopyOnWriteArrayList<FieldbusModule>();

	public enum ThisChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ThisChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public Wago() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				DigitalOutput.ChannelId.values(), //
				DigitalInput.ChannelId.values(), //
				ThisChannelId.values() //
		);
	}

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbusTcp modbus) {
		super.setModbus(modbus);
		this.ipAddress = modbus.getIpAddress();
	}

	private final ScheduledExecutorService configExecutor = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> configFuture = null;

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), UNIT_ID, this.cm, "Modbus",
				config.modbus_id())) {
			return;
		}

		/*
		 * Async Create Channels dynamically from ea-config.xml file
		 */
		this.configFuture = this.configExecutor.schedule(() -> {
			try {
				Document doc = Wago.downloadConfigXml(this.ipAddress, config.username(), config.password());
				this.modules.addAll(this.parseXml(doc));
				this.createProtocolFromModules(this.modules);

				this.logInfo(this.log, "Initialized WAGO Fieldbus Coupler 750-352");
				for (FieldbusModule module : this.modules) {
					this.logInfo(this.log, "Found [" + module.getName() + "]"//
							+ " with Channels [" //
							+ Stream.of(module.getChannels()) //
									.map(c -> c.address().toString()) //
									.collect(Collectors.joining(", ")) //
							+ "]");
				}

			} catch (SAXException | IOException | ParserConfigurationException | OpenemsException e) {
				e.printStackTrace();
			}
		}, 2, TimeUnit.SECONDS);
	}

	/**
	 * Downloads the config xml file from WAGO fieldbus coupler. Tries old
	 * ('ea-config.xml') and new ('io_config.xml') filenames.
	 * 
	 * @param ip       the IP address
	 * @param username the login username
	 * @param password the login password
	 * @return the XML document
	 * @throws SAXException                 on error
	 * @throws IOException                  on error
	 * @throws ParserConfigurationException on error
	 */
	private static Document downloadConfigXml(InetAddress ip, String username, String password)
			throws SAXException, IOException, ParserConfigurationException {
		try {
			return downloadConfigXml(ip, "ea-config.xml", username, password);
		} catch (FileNotFoundException e) {
			return downloadConfigXml(ip, "io_config.xml", username, password);
		}
	}

	private static Document downloadConfigXml(InetAddress ip, String filename, String username, String password)
			throws ParserConfigurationException, SAXException, IOException {
		URL url = new URL(String.format("http://%s/etc/%s", ip.getHostAddress(), filename));
		String authStr = String.format("%s:%s", username, password);
		byte[] bytesEncoded = Base64.getEncoder().encode(authStr.getBytes());
		String authEncoded = new String(bytesEncoded);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("Authorization", String.format("Basic %s", authEncoded));
		connection.setRequestProperty("Content-Type", "text/xml");
		connection.setRequestMethod("GET");
		connection.connect();
		InputStream is = connection.getInputStream();
		return parseXmlToDocument(is);
	}

	protected static Document parseXmlToDocument(InputStream is)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(is);
		doc.getDocumentElement().normalize();
		return doc;
	}

	/**
	 * Parses the config xml file.
	 * 
	 * @param doc the XML document
	 * @return a list of FieldbusModules
	 */
	protected List<FieldbusModule> parseXml(Document doc) {
		FieldbusModuleFactory factory = new FieldbusModuleFactory();
		List<FieldbusModule> result = new ArrayList<>();
		Element wagoElement = doc.getDocumentElement();
		int offset0 = 0;
		int offset512 = 512;

		// parse all "Module" XML elements
		NodeList moduleNodes = wagoElement.getElementsByTagName("Module");
		for (int i = 0; i < moduleNodes.getLength(); i++) {
			Node moduleNode = moduleNodes.item(i);
			// get "Module" node attributes
			NamedNodeMap moduleAttrs = moduleNode.getAttributes();
			String moduleArtikelnr = moduleAttrs.getNamedItem("ARTIKELNR").getNodeValue();
			String moduleType = moduleAttrs.getNamedItem("MODULETYPE").getNodeValue();
			if (moduleNode.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element moduleElement = (Element) moduleNode;
			// parse all "Kanal" XML elements inside the "Module" element
			NodeList kanalNodes = moduleElement.getElementsByTagName("Kanal");
			FieldbusModuleKanal[] kanals = new FieldbusModuleKanal[kanalNodes.getLength()];
			for (int j = 0; j < kanalNodes.getLength(); j++) {
				Node kanalNode = kanalNodes.item(j);
				if (kanalNode.getNodeType() != Node.ELEMENT_NODE) {
					continue;
				}
				NamedNodeMap kanalAttrs = kanalNode.getAttributes();
				String channelName = kanalAttrs.getNamedItem("CHANNELNAME").getNodeValue();
				String channelType = kanalAttrs.getNamedItem("CHANNELTYPE").getNodeValue();
				kanals[j] = new FieldbusModuleKanal(channelName, channelType);
			}
			// Create FieldbusModule instance using factory method
			FieldbusModule module = factory.of(this, moduleArtikelnr, moduleType, kanals, offset0, offset512);
			assert module.getInputCoil512Elements().length == module.getOutputCoil512Elements().length;
			offset0 += module.getInputCoil0Elements().length;
			offset512 += module.getOutputCoil512Elements().length;
			result.add(module);
		}
		return result;
	}

	/**
	 * Takes a list of FieldbusModules and adds Modbus tasks to the protocol.
	 * 
	 * @param modules lit of {@link FieldbusModule}s
	 * @throws OpenemsException on error
	 */
	protected void createProtocolFromModules(List<FieldbusModule> modules) throws OpenemsException {
		List<ModbusCoilElement> readCoilElements0 = new ArrayList<>();
		List<ModbusCoilElement> readCoilElements512 = new ArrayList<>();
		for (FieldbusModule module : modules) {
			for (ModbusCoilElement element : module.getInputCoil0Elements()) {
				readCoilElements0.add(element);
			}
			for (ModbusCoilElement element : module.getInputCoil512Elements()) {
				readCoilElements512.add(element);
			}
			for (ModbusCoilElement element : module.getOutputCoil512Elements()) {
				FC5WriteCoilTask writeCoilTask = new FC5WriteCoilTask(element.getStartAddress(), element);
				this.protocol.addTask(writeCoilTask);
			}
		}
		if (!readCoilElements0.isEmpty()) {
			this.protocol.addTask(new FC1ReadCoilsTask(0, Priority.LOW,
					readCoilElements0.toArray(new AbstractModbusElement<?>[readCoilElements0.size()])));
		}
		if (!readCoilElements512.isEmpty()) {
			this.protocol.addTask(new FC1ReadCoilsTask(512, Priority.LOW,
					readCoilElements512.toArray(new AbstractModbusElement<?>[readCoilElements512.size()])));
		}
	}

	/**
	 * Creates a Modbus {@link CoilElement} on the address and maps it to the given
	 * Channel-ID.
	 * 
	 * @param channelId the Channel-ID
	 * @param address   the modbus start address of the coil
	 * @return the modbus {@link CoilElement}
	 */
	protected CoilElement createModbusCoilElement(io.openems.edge.common.channel.ChannelId channelId, int address) {
		return m(channelId, new CoilElement(address));
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() throws OpenemsException {
		if (this.protocol == null) {
			this.protocol = new ModbusProtocol(this);
		}
		return this.protocol;
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();

		// Shutdown executor
		if (this.configFuture != null) {
			this.configFuture.cancel(true);
		}
		ThreadPoolUtils.shutdownAndAwaitTermination(this.configExecutor, 5);
	}

	@Override
	public String debugLog() {
		if (this.modules.isEmpty()) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < this.modules.size(); i++) {
			b.append("M" + i + ":");
			BooleanReadChannel[] channels = this.modules.get(i).getChannels();
			for (int j = 0; j < channels.length; j++) {
				Optional<Boolean> valueOpt = channels[j].value().asOptional();
				if (valueOpt.isPresent()) {
					b.append(valueOpt.get() ? "x" : "-");
				} else {
					b.append("?");
				}
			}
			if (i < this.modules.size() - 1) {
				b.append("|");
			}
		}
		return b.toString();
	}

	@Override
	public BooleanReadChannel[] digitalInputChannels() {
		List<BooleanReadChannel> channels = new ArrayList<>();
		for (FieldbusModule module : this.modules) {
			for (BooleanReadChannel channel : module.getChannels()) {
				channels.add(channel);
			}
		}
		BooleanReadChannel[] result = new BooleanReadChannel[channels.size()];
		for (int i = 0; i < channels.size(); i++) {
			result[i] = channels.get(i);
		}
		return result;
	}

	@Override
	public BooleanWriteChannel[] digitalOutputChannels() {
		List<BooleanWriteChannel> channels = new ArrayList<>();
		for (FieldbusModule module : this.modules) {
			for (BooleanReadChannel channel : module.getChannels()) {
				if (channel instanceof BooleanWriteChannel) {
					channels.add((BooleanWriteChannel) channel);
				}
			}
		}
		BooleanWriteChannel[] result = new BooleanWriteChannel[channels.size()];
		for (int i = 0; i < channels.size(); i++) {
			result[i] = channels.get(i);
		}
		return result;
	}

	protected BooleanReadChannel addChannel(FieldbusChannelId channelId) {
		return (BooleanReadChannel) super.addChannel(channelId);
	}
}

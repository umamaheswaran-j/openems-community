package io.openems.edge.bridge.modbus.sunspec;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.base.CaseFormat;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.function.ThrowingFunction;
import io.openems.common.utils.XmlUtils;
import io.openems.edge.bridge.modbus.sunspec.SunSpecCodeGenerator.Point.Symbol;
import io.openems.edge.bridge.modbus.sunspec.SunSpecPoint.PointCategory;
import io.openems.edge.bridge.modbus.sunspec.SunSpecPoint.PointType;

/**
 * This tool converts SunSpec XML definitions to Java code suitable for the
 * OpenEMS SunSpec implementation.
 * 
 * <p>
 * Download XML files from https://github.com/sunspec/models.
 */
public class SunSpecCodeGenerator {

	/**
	 * Path to the SunSpec model XML files; download them from
	 * https://github.com/sunspec/models.
	 */
	private static final String SUNSPEC_XML_PATH = System.getProperty("user.home") + "/git/sunspec/smdx/";
	/**
	 * Path to the generated output file.
	 */
	private static final String OUT_FILE_PATH = System.getProperty("user.home") + "/git/sunspec/smdx/SunSpecModel.java";

	/**
	 * XML files that should be ignored; mainly because certain features are not
	 * implemented yet.
	 */
	private static final Set<String> IGNORE_FILES = new HashSet<>(Arrays.asList(//
			"smdx_00003.xml", //
			"smdx_00004.xml", //
			"smdx_00005.xml", //
			"smdx_00006.xml", //
			"smdx_00007.xml", //
			"smdx_00008.xml", //
			"smdx_00009.xml", //
			"smdx_00010.xml", //
			"smdx_00011.xml", //
			"smdx_00012.xml", //
			"smdx_00013.xml", //
			"smdx_00014.xml", //
			"smdx_00016.xml", //
			"smdx_00017.xml", //
			"smdx_00019.xml", //
			"smdx_00126.xml", //
			"smdx_00129.xml", //
			"smdx_00130.xml", //
			"smdx_00131.xml", //
			"smdx_00132.xml", //
			"smdx_00133.xml", //
			"smdx_00134.xml", //
			"smdx_00135.xml", //
			"smdx_00136.xml", //
			"smdx_00137.xml", //
			"smdx_00138.xml", //
			"smdx_00139.xml", //
			"smdx_00140.xml", //
			"smdx_00141.xml", //
			"smdx_00142.xml", //
			"smdx_00143.xml", //
			"smdx_00144.xml", //
			"smdx_00160.xml", //
			"smdx_00211.xml", //
			"smdx_00212.xml", //
			"smdx_00213.xml", //
			"smdx_00214.xml", //
			"smdx_00220.xml", //
			"smdx_00401.xml", //
			"smdx_00402.xml", //
			"smdx_00403.xml", //
			"smdx_00404.xml", //
			"smdx_00501.xml", //
			"smdx_00502.xml", //
			"smdx_00601.xml", //
			"smdx_00803.xml", //
			"smdx_00804.xml", //
			"smdx_00805.xml", //
			"smdx_00806.xml", //
			"smdx_00807.xml", //
			"smdx_00808.xml", //
			"smdx_00809.xml", //
			"smdx_63001.xml", //
			"smdx_63002.xml", //
			"smdx_64020.xml" //
	));

	/**
	 * Run this method to start the code generator.
	 * 
	 * @param args not supported
	 * @throws Exception on error
	 */
	public static void main(String[] args) throws Exception {
		SunSpecCodeGenerator generator = new SunSpecCodeGenerator();
		List<Model> models = generator.parseSunSpecXmlFiles();
		generator.writeSunSpecModelJavaFile(models);
	}

	/**
	 * Parses all SunSpec XML files in a directory.
	 * 
	 * @return a list of Models
	 * @throws Exception on error
	 */
	private List<Model> parseSunSpecXmlFiles() throws Exception {
		List<Model> result = new ArrayList<>();
		for (File file : new File(SUNSPEC_XML_PATH).listFiles(file -> //
		file.getName().startsWith("smdx_") //
				&& file.getName().endsWith(".xml") //
				&& !IGNORE_FILES.contains(file.getName()))) {
			try {
				Model model = this.parseSunSpecXmlFile(file);
				result.add(model);

			} catch (Exception e) {
				throw new Exception("Error while reading from " + file, e);
			}
		}
		return result;
	}

	/**
	 * Parses a SunSpec XML file.
	 * 
	 * @param file the SunSpec XML file handler
	 * @return the Model
	 * @throws Exception on error
	 */
	private Model parseSunSpecXmlFile(File file) throws Exception {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(file);
		doc.getDocumentElement().normalize();

		SunSpecCodeGenerator generator = new SunSpecCodeGenerator();
		Model model = generator.parseSunSpecModels(doc.getDocumentElement());

		return model;
	}

	/**
	 * Parses the element sunSpecModels.
	 * 
	 * <pre>
	 *   &lt;sunSpecModels v="1"&gt;
	 * </pre>
	 * 
	 * <ul>
	 * <li>xs:attribute name="v" type="xs:string" default="1"
	 * </ul>
	 * 
	 * @param sunSpecModelsElement the 'sunSpecModels' node
	 * @return the Model
	 * @throws OpenemsNamedException on error
	 */
	private Model parseSunSpecModels(Element sunSpecModelsElement) throws OpenemsNamedException {
		// parse all "model" XML elements
		NodeList modelNodes = sunSpecModelsElement.getElementsByTagName("model");
		Node modelNode = this.assertExactlyOneNode(modelNodes, (node) -> node.getAttributes().getLength() != 0);
		Model model = this.parseModel(modelNode);

		// parse all "strings" XML elements
		NodeList stringsNodes = sunSpecModelsElement.getElementsByTagName("strings");
		Node stringsNode = this.assertExactlyOneNode(stringsNodes);
		this.parseStrings(stringsNode, model);

		return model;
	}

	/**
	 * Parses the element sunSpecModels -&gt; model.
	 * 
	 * <pre>
	 *   &lt;model id="1" len="66" name="common"&gt;
	 * </pre>
	 * 
	 * <ul>
	 * <li>xs:attribute name="id" type="xs:integer"
	 * <li>xs:attribute name="len" type="xs:integer"
	 * </ul>
	 * 
	 * @param node the 'model' node
	 * @return the Model
	 * @throws OpenemsNamedException on error
	 */
	private Model parseModel(Node node) throws OpenemsNamedException {
		// read attributes
		NamedNodeMap attrs = node.getAttributes();
		int id = XmlUtils.getAsInt(attrs, "id");
		int len = XmlUtils.getAsInt(attrs, "len");
		String name = XmlUtils.getAsStringOrElse(attrs, "name", "");

		// read points
		Element element = (Element) node;
		NodeList blockNodes = element.getElementsByTagName("block");
		Node blockNode = this.assertExactlyOneNode(blockNodes);
		List<Point> points = this.parseModelBlock(blockNode);

		return new Model(id, len, name, points);
	}

	/**
	 * Parses the element sunSpecModels -&gt; model -&gt; block.
	 * 
	 * <pre>
	 *   &lt;block len="66"&gt;
	 * </pre>
	 * 
	 * <ul>
	 * <li>xs:attribute name="id" type="xs:integer"
	 * <li>xs:attribute name="len" type="xs:integer"
	 * </ul>
	 * 
	 * @param node the 'block' node
	 * @return a list Points
	 * @throws OpenemsNamedException on error
	 */
	private List<Point> parseModelBlock(Node node) throws OpenemsNamedException {
		// TODO implement "repeating" blocks
		List<Point> points = new ArrayList<>();
		Element element = (Element) node;
		NodeList pointNodes = element.getElementsByTagName("point");
		for (int i = 0; i < pointNodes.getLength(); i++) {
			Node pointNode = pointNodes.item(i);
			points.add(this.parseModelBlockPoint(pointNode));
		}
		return points;
	}

	/**
	 * Parses the element sunSpecModels -&gt; model -&gt; block -&gt; point.
	 * 
	 * <pre>
	 *   &lt;point id="Mn" offset="0" type="string" len="16" mandatory="true" /&gt;
	 * </pre>
	 * 
	 * <ul>
	 * <li>xs:attribute name="id" type="xs:string" use="required"
	 * <li>xs:attribute name="len" type="xs:integer"
	 * <li>xs:attribute name="offset" type="xs:integer"
	 * <li>xs:attribute name="type" type="PointTypeDefinition"
	 * <li>xs:attribute name="sf" type="xs:string"
	 * <li>xs:attribute name="units" type="xs:string"
	 * <li>xs:attribute name="access" type="PointAccessDefinition" default="r"
	 * <li>xs:attribute name="mandatory" type="xs:boolean" default="false"
	 * <li>xs:attribute name="category" type="CategoryDefinition"
	 * default="measurement"
	 * </ul>
	 * 
	 * @param node the 'point' node.
	 * @return the Point
	 * @throws OpenemsNamedException on error
	 */
	private Point parseModelBlockPoint(Node node) throws OpenemsNamedException {
		NamedNodeMap attrs = node.getAttributes();

		int len;
		PointType type;
		String typeString = XmlUtils.getAsString(attrs, "type");
		if (typeString.equals("string")) {
			len = XmlUtils.getAsInt(attrs, "len");
			type = PointType.valueOf("STRING" + len);
		} else {
			type = XmlUtils.getAsEnum(PointType.class, attrs, "type");
			len = type.length;
		}

		String scaleFactor = XmlUtils.getAsStringOrElse(attrs, "sf", null);
		String unitString = XmlUtils.getAsStringOrElse(attrs, "units", "");
		final ThrowingFunction<String, Unit, OpenemsNamedException> toUnit = (s) -> {
			s = s.trim();
			if (s.contains(" ")) {
				s = s.substring(0, s.indexOf(" "));
			}
			switch (s) {
			case "":
			case "%ARtg/%dV":
			case "bps": // not available in OpenEMS
			case "cos()": // not available in OpenEMS
			case "deg": // not available in OpenEMS
			case "Degrees": // not available in OpenEMS
			case "hhmmss": // not available in OpenEMS
			case "hhmmss.sssZ": // not available in OpenEMS
			case "HPa": // not available in OpenEMS
			case "kO": // not available in OpenEMS
			case "Mbps": // not available in OpenEMS
			case "meters": // not available in OpenEMS
			case "mm": // not available in OpenEMS
			case "mps": // not available in OpenEMS
			case "m/s": // not available in OpenEMS
			case "ohms": // not available in OpenEMS
			case "Pct": // not available in OpenEMS
			case "PF": // not available in OpenEMS
			case "SF": // not available in OpenEMS
			case "text": // not available in OpenEMS
			case "Tmd": // not available in OpenEMS
			case "Tmh": // not available in OpenEMS
			case "Tms": // not available in OpenEMS
			case "Various": // not available in OpenEMS
			case "Vm": // not available in OpenEMS
			case "W/m2": // not available in OpenEMS
			case "YYYYMMDD": // not available in OpenEMS
				return Unit.NONE;
			case "%":
			case "%WHRtg":
				return Unit.PERCENT;
			case "A":
				return Unit.AMPERE;
			case "Ah":
			case "AH":
				return Unit.AMPERE_HOURS;
			case "C":
				return Unit.DEGREE_CELSIUS;
			case "Hz":
				return Unit.HERTZ;
			case "kAH":
				return Unit.KILOAMPERE_HOURS;
			case "kWh":
				return Unit.KILOWATT_HOURS;
			case "mSecs":
				return Unit.MILLISECONDS;
			case "Secs":
				return Unit.SECONDS;
			case "V":
				return Unit.VOLT;
			case "VA":
				return Unit.VOLT_AMPERE;
			case "VAh":
				return Unit.VOLT_AMPERE_HOURS;
			case "var":
				return Unit.VOLT_AMPERE_REACTIVE;
			case "varh":
				return Unit.VOLT_AMPERE_REACTIVE_HOURS;
			case "W":
				return Unit.WATT;
			case "Wh":
				return Unit.WATT_HOURS;
			}
			throw new OpenemsException("Unhandled unit [" + s + "]");
		};
		Unit unit = toUnit.apply(unitString);

		String accessModeString = XmlUtils.getAsStringOrElse(attrs, "access", "r");
		AccessMode accessMode;
		switch (accessModeString.toLowerCase()) {
		case "wo":
			accessMode = AccessMode.WRITE_ONLY;
			break;
		case "rw":
			accessMode = AccessMode.READ_WRITE;
			break;
		case "r":
		case "ro":
		default:
			accessMode = AccessMode.READ_ONLY;
			break;
		}
		boolean mandatory = XmlUtils.getAsBooleanOrElse(attrs, "mandatory", false);
		PointCategory category = XmlUtils.getAsEnumOrElse(PointCategory.class, attrs, "category",
				PointCategory.MEASUREMENT);

		// read symbols
		Element element = (Element) node;
		NodeList symbolNodes = element.getElementsByTagName("symbol");
		Symbol[] symbols;
		if (symbolNodes.getLength() > 0) {
			symbols = new Symbol[symbolNodes.getLength()];
			for (int i = 0; i < symbolNodes.getLength(); i++) {
				Node symbolNode = symbolNodes.item(i);
				symbols[i] = this.parseModelBlockPointSymbol(symbolNode);
			}
		} else {
			symbols = new Symbol[0];
		}

		String id = XmlUtils.getAsString(attrs, "id");
		int offset = XmlUtils.getAsInt(attrs, "offset");

		Point point = new Point(id, len, offset, type, scaleFactor, unit, accessMode, mandatory, category, symbols);
		return point;
	}

	/**
	 * Parses the element sunSpecModels -&gt; model -&gt; block -&gt; point -&gt;
	 * symbol.
	 * 
	 * <pre>
	 *   &lt;symbol id="OFF"&gt1&ltsymbol&gt;
	 * </pre>
	 * 
	 * <ul>
	 * <li>xs:attribute name="id" type="xs:string" use="required"
	 * </ul>
	 * 
	 * @param node the 'symbol' node
	 * @return the Symbol
	 * @throws OpenemsNamedException on error
	 */
	private Symbol parseModelBlockPointSymbol(Node node) throws OpenemsNamedException {
		NamedNodeMap attrs = node.getAttributes();
		String id = XmlUtils.getAsString(attrs, "id");
		int value = XmlUtils.getContentAsInt(node);
		return new Symbol(id, value);
	}

	/**
	 * Parses the element sunSpecModels -&gt; strings.
	 * 
	 * <pre>
	 *   &lt;strings id="1" locale="en"&gt;
	 * </pre>
	 * 
	 * <ul>
	 * <li>xs:attribute name="id" type="xs:integer" use="required"
	 * <li>xs:attribute name="locale" type="xs:string"
	 * </ul>
	 * 
	 * @param node  the 'strings' node
	 * @param model the Model, that needs to be completed.
	 * @throws OpenemsNamedException on error
	 */
	@SuppressWarnings("unused")
	private void parseStrings(Node node, Model model) throws OpenemsNamedException {
		// read attributes
		NamedNodeMap attrs = node.getAttributes();
		int id = XmlUtils.getAsInt(attrs, "id");
		String locale = XmlUtils.getAsString(attrs, "locale");

		if (model.id != id) {
			throw new OpenemsException("Model-IDs are not matching");
		}

		Element element = (Element) node;

		// read model
		NodeList modelNodes = element.getElementsByTagName("model");
		Node modelNode = this.assertExactlyOneNode(modelNodes);
		this.parseStringsModel(modelNode, model);

		// read points
		NodeList pointNodes = element.getElementsByTagName("point");
		for (int i = 0; i < pointNodes.getLength(); i++) {
			Node pointNode = pointNodes.item(i);
			this.parseStringsPoint(pointNode, model);
		}
	}

	/**
	 * Parses the element sunSpecModels -&gt; strings -&gt; model.
	 * 
	 * <pre>
	 *   &lt;model&gt;
	 * </pre>
	 * 
	 * @param node  the 'model' node.
	 * @param model the Model, that needs to be completed.
	 * @throws OpenemsNamedException on error
	 */
	private void parseStringsModel(Node node, Model model) throws OpenemsNamedException {
		model.label = this.getTextContent(node, "label");
		model.description = this.getTextContent(node, "description");
		model.notes = this.getTextContent(node, "notes");
	}

	/**
	 * Parses the element sunSpecModels -&gt; strings -&gt; point.
	 * 
	 * <pre>
	 *   &lt;point&gt;
	 * </pre>
	 * 
	 * @param node  the 'point' node
	 * @param model the Model, that needs to be completed.
	 * @throws OpenemsNamedException on error
	 */
	private void parseStringsPoint(Node node, Model model) throws OpenemsNamedException {
		NamedNodeMap attrs = node.getAttributes();
		String id = XmlUtils.getAsString(attrs, "id");
		switch (id) {
		case "VArWMaxPct_SF":
		case "TotVArhExpQ4Ph":
		case "PPVphAB":
		case "PPVphBC":
		case "PPVphCA":
		case "Pad1":
		case "Pad":
			// Special handling for IDs 123, 201, 202, 801, 802
			// TODO: create pull-request to fix XML file upstream
			return;
		}

		Point point = model.getPoint(id);

		Element element = (Element) node;
		NodeList subNodes = element.getChildNodes();
		for (int i = 0; i < subNodes.getLength(); i++) {
			Node subNode = subNodes.item(i);
			switch (subNode.getNodeName()) {
			case "label":
				point.label = XmlUtils.getContentAsString(subNode);
				break;
			case "description":
				point.description = XmlUtils.getContentAsString(subNode);
				break;
			case "notes":
				point.notes = XmlUtils.getContentAsString(subNode);
				break;
			case "symbol":
				this.parseStringsPointSymbol(subNode, point);
				break;
			case "#text":
				// ignore
				break;
			default:
				throw new OpenemsException("Unable to handle " + subNode.getNodeName());
			}
		}
	}

	/**
	 * Parses the element sunSpecModels -&gt; strings -&gt; point -&gt; symbol.
	 * 
	 * <pre>
	 *   &lt;point&gt;
	 * </pre>
	 * 
	 * @param node  the 'symbol' node
	 * @param point the Model, that needs to be completed.
	 * @throws OpenemsNamedException on error
	 */
	private void parseStringsPointSymbol(Node node, Point point) throws OpenemsNamedException {
		NamedNodeMap attrs = node.getAttributes();
		String id = XmlUtils.getAsString(attrs, "id");
		switch (id) {
		case "OEM16":
			// Special handling for ID 201
			// TODO: create pull-request to fix XML file upstream
			return;
		}

		Symbol symbol = point.getSymbol(id);

		Element element = (Element) node;
		NodeList subNodes = element.getChildNodes();
		for (int i = 0; i < subNodes.getLength(); i++) {
			Node subNode = subNodes.item(i);
			switch (subNode.getNodeName()) {
			case "label":
				symbol.label = XmlUtils.getContentAsString(subNode);
				break;
			case "description":
				symbol.description = XmlUtils.getContentAsString(subNode);
				break;
			case "notes":
				symbol.notes = XmlUtils.getContentAsString(subNode);
				break;
			case "#text":
				// ignore
				break;
			default:
				throw new OpenemsException("Unable to handle " + subNode.getNodeName());
			}
		}
	}

	/**
	 * Writes the SunSpecModel.java file.
	 * 
	 * @param models a list of Models
	 * @throws IOException on error
	 */
	private void writeSunSpecModelJavaFile(List<Model> models) throws IOException {
		try (BufferedWriter w = Files.newBufferedWriter(Paths.get(OUT_FILE_PATH))) {
			w.write("package io.openems.edge.bridge.modbus.sunspec;");
			w.newLine();
			w.newLine();
			w.write("import io.openems.common.channel.AccessMode;");
			w.newLine();
			w.write("import io.openems.common.channel.Unit;");
			w.newLine();
			w.write("import io.openems.common.types.OptionsEnum;");
			w.newLine();
			w.newLine();
			w.write("/**");
			w.newLine();
			w.write(" * Do not touch this file. It is auto-generated by SunSpecCodeGenerator.");
			w.newLine();
			w.write(" */");
			w.newLine();
			w.write("public enum SunSpecModel {");
			w.newLine();

			/*
			 * Write main Model enum
			 */
			for (int i = 0; i < models.size(); i++) {
				Model model = models.get(i);
				w.write("	S_" + model.id + "(//");
				w.newLine();
				w.write("			\"" + esc(model.label) + "\", //");
				w.newLine();
				w.write("			\"" + esc(model.description) + "\", //");
				w.newLine();
				w.write("			\"" + esc(model.notes) + "\", //");
				w.newLine();
				w.write("			" + model.len + ", //");
				w.newLine();
				w.write("			SunSpecModel.S" + model.id + ".values(), //");
				w.newLine();
				w.write("			SunSpecModelType." + model.modelType + " //");
				w.newLine();
				w.write("	)");
				if (i == models.size() - 1) {
					w.write("; //");
				} else {
					w.write(", //");
				}
				w.newLine();
			}
			w.newLine();

			/*
			 * For each Model write enum with SunSpecPoints
			 */
			for (Model model : models) {
				w.write("	public static enum S" + model.id + " implements SunSpecPoint {");
				w.newLine();
				for (int i = 0; i < model.points.size(); i++) {
					Point point = model.points.get(i);
					String pointUpperId = toUpperUnderscore(point.id);
					w.write("		" + pointUpperId + "(new PointImpl(//");
					w.newLine();
					w.write("				\"S" + model.id + "_" + pointUpperId + "\", //");
					w.newLine();
					w.write("				\"" + esc(point.label) + "\", //");
					w.newLine();
					w.write("				\"" + esc(point.description) + "\", //");
					w.newLine();
					w.write("				\"" + esc(point.notes) + "\", //");
					w.newLine();
					w.write("				PointType." + point.type.name() + ", //");
					w.newLine();
					w.write("				" + point.mandatory + ", //");
					w.newLine();
					w.write("				AccessMode." + point.accessMode.name() + ", //");
					w.newLine();
					w.write("				Unit." + point.unit.name() + ", //");
					w.newLine();
					w.write("				"
							+ (point.scaleFactor.isPresent() ? ("\"" + point.scaleFactor.get() + "\"") : null)
							+ ", //");
					w.newLine();
					if (point.symbols.length == 0) {
						w.write("				new OptionsEnum[0]))");
					} else {
						w.write("				S" + model.id + "_" + point.id + ".values()))");
					}

					if (i == model.points.size() - 1) {
						w.write("; //");
					} else {
						w.write(", //");
					}
					w.newLine();
				}
				w.newLine();
				w.write("		protected final PointImpl impl;");
				w.newLine();
				w.newLine();
				w.write("		private S" + model.id + "(PointImpl impl) {");
				w.newLine();
				w.write("			this.impl = impl;");
				w.newLine();
				w.write("		}");
				w.newLine();
				w.newLine();
				w.write("		@Override");
				w.newLine();
				w.write("		public PointImpl get() {");
				w.newLine();
				w.write("			return this.impl;");
				w.newLine();
				w.write("		}");
				w.newLine();
				w.write("	}");
				w.newLine();
				w.newLine();

				/*
				 * For SunSpecPoints with Symbols write OpenEMS OptionsEnum
				 */
				for (Point point : model.points) {
					if (point.symbols.length == 0) {
						continue;
					}

					w.write("	public static enum S" + model.id + "_" + point.id + " implements OptionsEnum {");
					w.newLine();
					w.write("		UNDEFINED(-1, \"Undefined\"), //");
					w.newLine();
					for (int i = 0; i < point.symbols.length; i++) {
						Symbol symbol = point.symbols[i];
						String symbolId = symbol.id;
						symbolId = toUpperUnderscore(symbolId);

						switch (symbolId) {
						case "RESERVED":
							symbolId = symbolId + "_" + symbol.value; // avoid duplicated "RESERVED" ids.
							break;
						}

						w.write("		" + symbolId + "(" + symbol.value + ", \"" + symbolId + "\")");
						if (i == point.symbols.length - 1) {
							w.write("; //");
						} else {
							w.write(", //");
						}
						w.newLine();
					}
					w.newLine();
					w.write("		private final int value;");
					w.newLine();
					w.write("		private final String name;");
					w.newLine();
					w.newLine();
					w.write("		private S" + model.id + "_" + point.id + "(int value, String name) {");
					w.newLine();
					w.write("			this.value = value;");
					w.newLine();
					w.write("			this.name = name;");
					w.newLine();
					w.write("		}");
					w.newLine();
					w.newLine();
					w.write("		@Override");
					w.newLine();
					w.write("		public int getValue() {");
					w.newLine();
					w.write("			return value;");
					w.newLine();
					w.write("		}");
					w.newLine();
					w.newLine();
					w.write("		@Override");
					w.newLine();
					w.write("		public String getName() {");
					w.newLine();
					w.write("			return name;");
					w.newLine();
					w.write("		}");
					w.newLine();
					w.newLine();
					w.write("		@Override");
					w.newLine();
					w.write("		public OptionsEnum getUndefined() {");
					w.newLine();
					w.write("			return UNDEFINED;");
					w.newLine();
					w.write("		}");
					w.newLine();
					w.write("	}");
					w.newLine();
					w.newLine();
				}
			}

			w.write("	public final String label;");
			w.newLine();
			w.write("	public final String description;");
			w.newLine();
			w.write("	public final String notes;");
			w.newLine();
			w.write("	public final int length;");
			w.newLine();
			w.write("	public final SunSpecPoint[] points;");
			w.newLine();
			w.write("	public final SunSpecModelType modelType;");
			w.newLine();
			w.newLine();
			w.write("	private SunSpecModel(String label, String description, String notes, int length, SunSpecPoint[] points,");
			w.newLine();
			w.write("			SunSpecModelType modelType) {");
			w.newLine();
			w.write("		this.label = label;");
			w.newLine();
			w.write("		this.description = description;");
			w.newLine();
			w.write("		this.notes = notes;");
			w.newLine();
			w.write("		this.length = length;");
			w.newLine();
			w.write("		this.points = points;");
			w.newLine();
			w.write("		this.modelType = modelType;");
			w.newLine();
			w.write("	}");
			w.newLine();
			w.write("}");
			w.newLine();
		}
	}

	/**
	 * Helper method to escape a string.
	 * 
	 * @param string original string
	 * @return escaped string
	 */
	private static final String esc(String string) {
		if (string == null) {
			return "";
		}
		return string //
				.replaceAll("[^\\x00-\\x7F]", "") // non-ascii chars
				.replace("\"", "\\\"") // escape backslash
				.trim();
	}

	/**
	 * Throws an exception if the list does not have exactly one Node that matches
	 * the filters. Returns that node otherwise.
	 * 
	 * @param nodes   the list of nodes
	 * @param filters the filters that need to be matched by the node
	 * @return the Node
	 * @throws IllegalArgumentException if not exactly one matching Node was found
	 */
	@SafeVarargs
	private final Node assertExactlyOneNode(NodeList nodes, Function<Node, Boolean>... filters) {
		if (nodes.getLength() == 1) {
			return nodes.item(0);
		}
		Node result = null;
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			for (Function<Node, Boolean> filter : filters) {
				if (filter.apply(node)) {
					if (result != null) {
						throw new IllegalArgumentException("Exactly one node matching the filters was expected!");
					}
					result = node;
				}
			}
		}

		if (result != null) {
			return result;
		} else {
			throw new IllegalArgumentException("Exactly one node matching the filters was expected!");
		}
	}

	/**
	 * Gets the Content of a Sub-Node.
	 * 
	 * @param node    the Node
	 * @param tagName the tag name of the Sub-Node
	 * @return the Content as a String
	 */
	private String getTextContent(Node node, String tagName) {
		Element element = (Element) node;
		NodeList nodes = element.getElementsByTagName(tagName);
		Node subNode = this.assertExactlyOneNode(nodes);
		return subNode.getTextContent();
	}

	/**
	 * POJO container for a SunSpec Model.
	 */
	public static class Model {
		protected final int id;
		protected final int len;
		protected final String name;
		protected final List<Point> points;
		protected final SunSpecModelType modelType;

		protected String label = "";
		protected String description = "";
		protected String notes = "";

		public Model(int id, int len, String name, List<Point> points) {
			this.id = id;
			this.len = len;
			this.name = name;
			this.points = points;
			this.modelType = SunSpecModelType.getModelType(id);
		}

		/**
		 * Gets the Point with the given Id.
		 * 
		 * @param id the Point-ID
		 * @return the Point
		 * @throws OpenemsException on error
		 */
		public Point getPoint(String id) throws OpenemsException {
			for (Point point : this.points) {
				if (point.id.equals(id)) {
					return point;
				}
			}
			throw new OpenemsException("Unable to find Point with ID " + id);
		}

		@Override
		public String toString() {
			return "Model [id=" + this.id + ", len=" + this.len + ", name=" + this.name + ", points=" + this.points
					+ ", label=" + this.label + ", description=" + this.description + ", notes=" + this.notes + "]";
		}

	}

	/**
	 * POJO container for a SunSpec Point.
	 */
	public static class Point {

		protected final String id;
		protected final int len;
		protected final int offset;
		protected final PointType type;
		protected final Optional<String> scaleFactor;
		protected final Unit unit;
		protected final AccessMode accessMode;
		protected final boolean mandatory;
		protected final PointCategory category;
		protected final Symbol[] symbols;

		protected String label;
		protected String description;
		protected String notes;

		public Point(String id, int len, int offset, PointType type, String scaleFactor, Unit unit,
				AccessMode accessMode, boolean mandatory, PointCategory category, Symbol[] symbols) {
			super();
			this.id = id;
			this.len = len;
			this.offset = offset;
			this.type = type;
			this.scaleFactor = Optional.ofNullable(scaleFactor);
			this.unit = unit;
			this.accessMode = accessMode;
			this.mandatory = mandatory;
			this.category = category;
			this.symbols = symbols;
		}

		/**
		 * Gets the Symbol with the given Id.
		 * 
		 * @param id the Symbol-Id
		 * @return the Symbol
		 * @throws OpenemsException on error
		 */
		public Symbol getSymbol(String id) throws OpenemsException {
			for (Symbol symbol : this.symbols) {
				if (symbol.id.equals(id)) {
					return symbol;
				}
			}
			throw new OpenemsException("Unable to find Symbol with ID " + id);
		}

		@Override
		public String toString() {
			return "Point [id=" + this.id + ", len=" + this.len + ", offset=" + this.offset + ", type=" + this.type
					+ ", scaleFactor=" + this.scaleFactor.orElse("") + ", unit=" + this.unit + ", access="
					+ this.accessMode + ", mandatory=" + this.mandatory + ", category=" + this.category + ", symbols="
					+ Arrays.toString(this.symbols) + ", label=" + this.label + ", description=" + this.description
					+ ", notes=" + this.notes + "]";
		}

		/**
		 * POJO container for a SunSpec Point Symbol.
		 */
		public static class Symbol {
			protected final String id;
			protected final int value;

			protected String label;
			protected String description;
			protected String notes;

			private static Function<String, String> idCleaner = (id) -> {
				switch (id) {
				case "ggOFF":
				case "ggSLEEPING":
				case "ggSTARTING":
				case "ggTHROTTLED":
				case "ggSHUTTING_DOWN":
				case "ggFAULT":
				case "ggSTANDBY":
					// Special handling for ID 111 point "Operating State"
					// TODO: create pull-request to fix XML file upstream
					return id.substring(2);
				case "M_EVENT_Power_Failure":
				case "M_EVENT_Under_Voltage":
				case "M_EVENT_Low_PF":
				case "M_EVENT_Over_Current":
				case "M_EVENT_Over_Voltage":
				case "M_EVENT_Missing_Sensor":
				case "M_EVENT_Reserved1":
				case "M_EVENT_Reserved2":
				case "M_EVENT_Reserved3":
				case "M_EVENT_Reserved4":
				case "M_EVENT_Reserved5":
				case "M_EVENT_Reserved6":
				case "M_EVENT_Reserved7":
				case "M_EVENT_Reserved8":
				case "M_EVENT_OEM01":
				case "M_EVENT_OEM02":
				case "M_EVENT_OEM03":
				case "M_EVENT_OEM04":
				case "M_EVENT_OEM05":
				case "M_EVENT_OEM06":
				case "M_EVENT_OEM07":
				case "M_EVENT_OEM08":
				case "M_EVENT_OEM09":
				case "M_EVENT_OEM10":
				case "M_EVENT_OEM11":
				case "M_EVENT_OEM12":
				case "M_EVENT_OEM13":
				case "M_EVENT_OEM14":
				case "M_EVENT_OEM15":
					// Special handling for ID 202 point "Events"
					return id.substring(8);
				default:
					return id;
				}
			};

			protected Symbol(String id, int value) {
				this.id = idCleaner.apply(id);
				this.value = value;
			}
		}
	}

	protected static String toUpperUnderscore(String string) {
		string = string //
				.replace("-", "_") //
				.replace(" ", "_");
		if (!string.toUpperCase().equals(string)) {
			string = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, string);
		}
		return string.replaceAll("__", "_");
	}

}

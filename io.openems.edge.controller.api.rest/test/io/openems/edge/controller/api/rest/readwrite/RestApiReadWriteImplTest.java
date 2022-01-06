package io.openems.edge.controller.api.rest.readwrite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.request.GetEdgeConfigRequest;
import io.openems.common.session.Role;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerDoc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyUser;
import io.openems.edge.common.test.DummyUserService;
import io.openems.edge.common.test.TestUtils;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.timedata.test.DummyTimedata;

public class RestApiReadWriteImplTest {

	private static final String CTRL_ID = "ctrlApiRest0";
	private static final String DUMMY_ID = "dummy0";

	private static final String GUEST = "guest";
	private static final String OWNER = "owner";
	private static final String INSTALLER = "installer";
	private static final String ADMIN = "admin";

	@Test
	public void test() throws OpenemsException, Exception {
		final int port = TestUtils.findRandomOpenPortOnAllLocalInterfaces();

		RestApiReadWriteImpl sut = new RestApiReadWriteImpl();
		ControllerTest test = new ControllerTest(sut) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("userService", new DummyUserService(//
						new DummyUser(GUEST, GUEST, Role.GUEST), //
						new DummyUser(OWNER, OWNER, Role.OWNER), //
						new DummyUser(INSTALLER, INSTALLER, Role.INSTALLER), //
						new DummyUser(ADMIN, ADMIN, Role.ADMIN))) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.addComponent(new DummyComponent(DUMMY_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setApiTimeout(60) //
						.setConnectionlimit(5) //
						.setDebugMode(false) //
						.setPort(port) //
						.build());

		/*
		 * /rest/channel/*
		 */
		// GET successful as GUEST
		JsonElement channelGet = sendGetRequest(port, GUEST, "/rest/channel/dummy0/ReadChannel");
		assertEquals(JsonUtils.buildJsonObject() //
				.addProperty("address", "dummy0/ReadChannel") //
				.addProperty("type", "INTEGER") //
				.addProperty("accessMode", "RO") //
				.addProperty("text", "This is a Read-Channel") //
				.addProperty("unit", "W") //
				.addProperty("value", 1234) //
				.build(), channelGet);

		// POST successful as OWNER
		JsonElement channelPost = sendPostRequest(port, OWNER, "/rest/channel/dummy0/WriteChannel",
				JsonUtils.buildJsonObject() //
						.addProperty("value", 4321) //
						.build());
		assertEquals(new JsonObject(), channelPost);
		test //
				.next(new TestCase() //
						.output(new ChannelAddress("dummy0", "WriteChannel"), 4321) //
						.output(new ChannelAddress(CTRL_ID, "ApiWorkerLog"), "dummy0/WriteChannel:4321"));

		// POST fails as GUEST
		try {
			sendPostRequest(port, GUEST, "/rest/channel/dummy0/WriteChannel", JsonUtils.buildJsonObject() //
					.addProperty("value", 4321) //
					.build());
			assertTrue(false);
		} catch (OpenemsNamedException e) {

		}

		/*
		 * JSON-RPC
		 */
		// POST fails as INSTALLER
		try {
			sendPostRequest(port, INSTALLER, "/jsonrpc", new GetEdgeConfigRequest().toJsonObject());
			assertTrue(false);
		} catch (OpenemsNamedException e) {

		}

		sut.deactivate();
	}

	private static JsonElement sendGetRequest(int port, String password, String endpoint) throws OpenemsNamedException {
		return sendRequest(port, "GET", password, endpoint, null);
	}

	private static JsonElement sendPostRequest(int port, String password, String endpoint, JsonObject request)
			throws OpenemsNamedException {
		return sendRequest(port, "POST", password, endpoint, request);
	}

	private static JsonElement sendRequest(int port, String requestMethod, String password, String endpoint,
			JsonObject request) throws OpenemsNamedException {
		try {
			URL url = new URL("http://127.0.0.1:" + port + endpoint);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestProperty("Authorization",
					"Basic " + new String(Base64.getEncoder().encode(("x:" + password).getBytes())));
			con.setRequestMethod(requestMethod);
			con.setRequestProperty("Content-Type", "application/json; utf-8");
			con.setRequestProperty("Accept", "application/json");
			con.setConnectTimeout(5000);
			con.setReadTimeout(50000);
			if (request != null) {
				con.setDoOutput(true);
				try (OutputStream os = con.getOutputStream()) {
					byte[] input = request.toString().getBytes("utf-8");
					os.write(input, 0, input.length);
				}
			}

			int status = con.getResponseCode();
			String body;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
				// Read HTTP response
				StringBuilder content = new StringBuilder();
				String line;
				while ((line = in.readLine()) != null) {
					content.append(line);
					content.append(System.lineSeparator());
				}
				body = content.toString();
			}
			if (status < 300) {
				// Parse response to JSON
				return JsonUtils.parse(body);
			} else {
				throw new OpenemsException("Error while reading from API. Response code: " + status + ". " + body);
			}
		} catch (OpenemsNamedException | IOException e) {
			throw new OpenemsException(
					"Unable to read from API. " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static class DummyComponent extends AbstractOpenemsComponent implements OpenemsComponent {

		private static enum ChannelId implements io.openems.edge.common.channel.ChannelId {
			READ_CHANNEL(new IntegerDoc() //
					.unit(Unit.WATT) //
					.initialValue(1234) //
					.text("This is a Read-Channel")), //
			WRITE_CHANNEL(Doc.of(OpenemsType.INTEGER) //
					.accessMode(AccessMode.READ_WRITE)); //

			private final Doc doc;

			private ChannelId(Doc doc) {
				this.doc = doc;
			}

			@Override
			public Doc doc() {
				return this.doc;
			}
		}

		protected DummyComponent(String id, String alias,
				io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
				io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
			super(firstInitialChannelIds, furtherInitialChannelIds);
			for (Channel<?> channel : this.channels()) {
				channel.nextProcessImage();
			}
			super.activate(null, id, alias, true);
		}

		public DummyComponent(String id) {
			this(id, "", //
					OpenemsComponent.ChannelId.values(), //
					DummyComponent.ChannelId.values() //
			);
		}
	}
}

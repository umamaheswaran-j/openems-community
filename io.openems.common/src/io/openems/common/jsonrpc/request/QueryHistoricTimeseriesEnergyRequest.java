package io.openems.common.jsonrpc.request;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.types.ChannelAddress;
import io.openems.common.utils.DateUtils;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Request for 'queryHistoricTimeseriesEnergy'.
 * 
 * <p>
 * This Request is for use-cases where you want to get the energy for one period
 * per Channel, e.g. to show the entire energy over a period as a text. The
 * energy is calculated by subtracting first value of the period ('fromDate')
 * from the last value of the period ('toDate').
 * 
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "queryHistoricTimeseriesEnergy",
 *   "params": {
 *     "timezone": Number,
 *     "fromDate": YYYY-MM-DD,
 *     "toDate": YYYY-MM-DD,
 *     "channels": ChannelAddress[]
 *   }
 * }
 * </pre>
 */

public class QueryHistoricTimeseriesEnergyRequest extends JsonrpcRequest {

	public static final String METHOD = "queryHistoricTimeseriesEnergy";

	/**
	 * Create {@link AuthenticateWithPasswordRequest} from a template
	 * {@link JsonrpcRequest}.
	 * 
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link AuthenticateWithPasswordRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static QueryHistoricTimeseriesEnergyRequest from(JsonrpcRequest r) throws OpenemsNamedException {
		JsonObject p = r.getParams();
		int timezoneDiff = JsonUtils.getAsInt(p, "timezone");
		ZoneId timezone = ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(timezoneDiff * -1));
		ZonedDateTime fromDate = JsonUtils.getAsZonedDateTime(p, "fromDate", timezone);
		ZonedDateTime toDate = JsonUtils.getAsZonedDateTime(p, "toDate", timezone).plusDays(1);
		QueryHistoricTimeseriesEnergyRequest result = new QueryHistoricTimeseriesEnergyRequest(r, fromDate, toDate);
		JsonArray channels = JsonUtils.getAsJsonArray(p, "channels");
		for (JsonElement channel : channels) {
			ChannelAddress address = ChannelAddress.fromString(JsonUtils.getAsString(channel));
			result.addChannel(address);
		}
		return result;
	}

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

	private final int timezoneDiff;
	private final ZonedDateTime fromDate;
	private final ZonedDateTime toDate;
	private final TreeSet<ChannelAddress> channels = new TreeSet<>();

	private QueryHistoricTimeseriesEnergyRequest(JsonrpcRequest request, ZonedDateTime fromDate, ZonedDateTime toDate)
			throws OpenemsNamedException {
		super(request, METHOD);

		DateUtils.assertSameTimezone(fromDate, toDate);
		this.timezoneDiff = ZoneOffset.from(fromDate).getTotalSeconds();
		this.fromDate = fromDate;
		this.toDate = toDate;
	}

	public QueryHistoricTimeseriesEnergyRequest(ZonedDateTime fromDate, ZonedDateTime toDate)
			throws OpenemsNamedException {
		super(METHOD);

		DateUtils.assertSameTimezone(fromDate, toDate);
		this.timezoneDiff = ZoneOffset.from(fromDate).getTotalSeconds();
		this.fromDate = fromDate;
		this.toDate = toDate;
	}

	private void addChannel(ChannelAddress address) {
		this.channels.add(address);
	}

	@Override
	public JsonObject getParams() {
		JsonArray channels = new JsonArray();
		for (ChannelAddress address : this.channels) {
			channels.add(address.toString());
		}
		return JsonUtils.buildJsonObject().addProperty("timezone", this.timezoneDiff) //
				.addProperty("fromDate", FORMAT.format(this.fromDate)) //
				.addProperty("toDate", FORMAT.format(this.toDate)) //
				.add("channels", channels) //
				.build();
	}

	/**
	 * Gets the From-Date.
	 * 
	 * @return From-Date
	 */
	public ZonedDateTime getFromDate() {
		return this.fromDate;
	}

	/**
	 * Gets the To-Date.
	 * 
	 * @return To-Date
	 */
	public ZonedDateTime getToDate() {
		return this.toDate;
	}

	/**
	 * Gets the {@link ChannelAddress}es.
	 * 
	 * @return Set of {@link ChannelAddress}
	 */
	public TreeSet<ChannelAddress> getChannels() {
		return this.channels;
	}

}

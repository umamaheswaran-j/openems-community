package io.openems.common.jsonrpc.request;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
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
 * Represents a JSON-RPC Request for 'queryHistoricTimeseriesData'.
 * 
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "queryHistoricTimeseriesData",
 *   "params": {
 *     "timezone": Number,
 *     "fromDate": YYYY-MM-DD,
 *     "toDate": YYYY-MM-DD,
 *     "channels": ChannelAddress[],
 *     "resolution"?: Number
 *   }
 * }
 * </pre>
 */
public class QueryHistoricTimeseriesDataRequest extends JsonrpcRequest {

	public static final String METHOD = "queryHistoricTimeseriesData";

	/**
	 * Create {@link QueryHistoricTimeseriesDataRequest} from a template
	 * {@link JsonrpcRequest}.
	 * 
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link QueryHistoricTimeseriesDataRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static QueryHistoricTimeseriesDataRequest from(JsonrpcRequest r) throws OpenemsNamedException {
		JsonObject p = r.getParams();
		int timezoneDiff = JsonUtils.getAsInt(p, "timezone");
		ZoneId timezone = ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(timezoneDiff * -1));
		ZonedDateTime fromDate = JsonUtils.getAsZonedDateTime(p, "fromDate", timezone);
		ZonedDateTime toDate = JsonUtils.getAsZonedDateTime(p, "toDate", timezone).plusDays(1);
		Integer resolution = JsonUtils.getAsOptionalInt(p, "resolution").orElse(null);
		QueryHistoricTimeseriesDataRequest result = new QueryHistoricTimeseriesDataRequest(r, fromDate, toDate,
				resolution);
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

	/**
	 * Resolution of the data in seconds or null for automatic.
	 */
	private final Integer resolution;

	private QueryHistoricTimeseriesDataRequest(JsonrpcRequest request, ZonedDateTime fromDate, ZonedDateTime toDate,
			Integer resolution) throws OpenemsNamedException {
		super(request, METHOD);

		DateUtils.assertSameTimezone(fromDate, toDate);
		this.timezoneDiff = ZoneOffset.from(fromDate).getTotalSeconds();
		this.fromDate = fromDate;
		this.toDate = toDate;
		this.resolution = resolution;
	}

	public QueryHistoricTimeseriesDataRequest(ZonedDateTime fromDate, ZonedDateTime toDate, Integer resolution)
			throws OpenemsNamedException {
		super(METHOD);

		DateUtils.assertSameTimezone(fromDate, toDate);
		this.timezoneDiff = ZoneOffset.from(fromDate).getTotalSeconds();
		this.fromDate = fromDate;
		this.toDate = toDate;
		this.resolution = resolution;
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
		return JsonUtils.buildJsonObject() //
				.addProperty("timezone", this.timezoneDiff) //
				.addProperty("fromDate", FORMAT.format(this.fromDate)) //
				.addProperty("toDate", FORMAT.format(this.toDate)) //
				.add("channels", channels) //
				.addPropertyIfNotNull("resolution", this.resolution) //
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

	/**
	 * Gets the requested Resolution in [s].
	 * 
	 * @return Resolution
	 */
	public Optional<Integer> getResolution() {
		return Optional.ofNullable(this.resolution);
	}

}

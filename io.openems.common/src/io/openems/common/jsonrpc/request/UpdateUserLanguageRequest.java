package io.openems.common.jsonrpc.request;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.utils.JsonUtils;

/**
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": UUID,
 *   "method": "updateUserLanguage",
 *   "params": {
 *      "language": {@link Language}
 *   }
 * }
 * </pre>
 */
public class UpdateUserLanguageRequest extends JsonrpcRequest {

	public static enum Language {
		EN, //
		DE, //
		CZ, //
		NL, //
		ES, //
		FR;

		/**
		 * Get {@link Language} for given key of the language or throws an exception.
		 * The given key is removed all leading and trailing whitespaces and converts
		 * all characters to upper case.
		 * 
		 * @param languageKey to get the {@link Language}
		 * @return the founded {@link Language} or throws an exception
		 * @throws OpenemsException on error
		 */
		public static Language from(String languageKey) throws OpenemsException {
			try {
				return Language.valueOf(languageKey.trim().toUpperCase());
			} catch (IllegalArgumentException ex) {
				throw new OpenemsException("Language [" + languageKey + "] not supported");
			}
		}

	}

	public static final String METHOD = "updateUserLanguage";

	/**
	 * Create {@link UpdateUserLanguageRequest} from a template
	 * {@link JsonrpcRequest}.
	 * 
	 * @param request the template {@link JsonrpcRequest}
	 * @return the {@link UpdateUserLanguageRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static UpdateUserLanguageRequest from(JsonrpcRequest request) throws OpenemsNamedException {
		JsonObject params = request.getParams();
		String language = JsonUtils.getAsString(params, "language");
		return new UpdateUserLanguageRequest(request, Language.from(language));
	}

	private final Language language;

	private UpdateUserLanguageRequest(JsonrpcRequest request, Language language) throws OpenemsException {
		super(request, METHOD);
		this.language = language;
	}

	@Override
	public JsonObject getParams() {
		return JsonUtils.buildJsonObject() //
				.addProperty("language", this.language.name()) //
				.build();
	}

	public Language getLanguage() {
		return this.language;
	}

}

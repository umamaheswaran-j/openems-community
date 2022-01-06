package io.openems.edge.core.host;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.function.ThrowingConsumer;
import io.openems.common.types.ConfigurationProperty;
import io.openems.common.utils.StringUtils;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.core.host.NetworkInterface.Inet4AddressWithNetmask;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemCommandRequest;
import io.openems.edge.core.host.jsonrpc.ExecuteSystemCommandResponse;
import io.openems.edge.core.host.jsonrpc.SetNetworkConfigRequest;

/**
 * OperatingSystem implementation for Debian with systemd.
 */
public class OperatingSystemDebianSystemd implements OperatingSystem {

	private static final String NETWORK_BASE_PATH = "/etc/systemd/network";
	private static final Path UDEV_PATH = Paths.get("/etc/udev/rules.d/99-usb-serial.rules");

	private static enum Block {
		UNDEFINED, MATCH, NETWORK
	}

	private final HostImpl parent;

	protected OperatingSystemDebianSystemd(HostImpl parent) {
		this.parent = parent;
	}

	/**
	 * Gets the current network configuration for systemd-networkd.
	 * 
	 * @return the current network configuration
	 * @throws OpenemsException on error
	 */
	@Override
	public NetworkConfiguration getNetworkConfiguration() throws OpenemsNamedException {
		File path = Paths.get(NETWORK_BASE_PATH).toFile();
		if (!path.exists()) {
			throw new OpenemsException("Base-Path [" + path + "] does not exist.");
		}

		TreeMap<String, NetworkInterface<?>> interfaces = new TreeMap<>();

		for (final File file : path.listFiles()) {
			/*
			 * Read all systemd network configuration files
			 */
			if (file.isDirectory() || !file.getName().endsWith(".network")) {
				continue;
			}
			try {
				/*
				 * Parse the content of the network configuration file
				 */
				List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.US_ASCII);
				NetworkInterface<File> networkInterface = parseSystemdNetworkdConfigurationFile(lines, file);

				// check for null value
				TypeUtils.assertNull("Network interface Name", networkInterface.getName());

				// add to result
				interfaces.put(networkInterface.getName(), networkInterface);

			} catch (IllegalArgumentException | IOException e) {
				throw new OpenemsException("Unable to read file [" + file + "]: " + e.getMessage());
			}
		}

		return new NetworkConfiguration(interfaces);
	}

	/**
	 * Handles a SetNetworkConfigRequest for systemd-networkd.
	 * 
	 * @param oldNetworkConfiguration the current/old network configuration
	 * @param request                 the JSON-RPC request
	 * @throws OpenemsException on error
	 */
	@Override
	public void handleSetNetworkConfigRequest(NetworkConfiguration oldNetworkConfiguration,
			SetNetworkConfigRequest request) throws OpenemsNamedException {
		boolean isChanged = false;
		List<NetworkInterface<?>> networkInterfaces = request.getNetworkInterface();
		for (NetworkInterface<?> networkInterface : networkInterfaces) {
			NetworkInterface<?> iface = oldNetworkConfiguration.getInterfaces().get(networkInterface.getName());
			if (iface == null) {
				throw new OpenemsException("No network interface with name [" + networkInterface.getName() + "]");
			}
			if (iface.updateFrom(networkInterface)) {
				isChanged = true;
			}
		}

		// stop early if there are no changes
		if (!isChanged) {
			throw new OpenemsException("Received no changes to network configuration");
		}

		// write configuration files
		IOException writeException = null;
		for (Entry<String, NetworkInterface<?>> entry : oldNetworkConfiguration.getInterfaces().entrySet()) {
			NetworkInterface<?> iface = entry.getValue();
			File file = (File) iface.getAttachment();
			List<String> lines = this.toFileFormat(iface);
			try {
				Files.write(file.toPath(), lines, StandardCharsets.US_ASCII);
			} catch (IOException e) {
				writeException = e;
			}
		}

		// did an exception happen while writing?
		if (writeException != null) {
			throw new OpenemsException("Unable to write file. " + writeException.getClass().getSimpleName() + ": "
					+ writeException.getMessage() + ". Network configuration might be inconsistent!");
		}

		// apply the configuration by restarting the systemd-networkd service
		this.handleExecuteCommandRequest(ExecuteSystemCommandRequest
				.runInBackgroundWithoutAuthentication("systemctl restart systemd-networkd --no-block"));
	}

	/**
	 * Helper function to match a String in the configuration file.
	 * 
	 * @param pattern  the regular expression pattern
	 * @param line     the line of the file
	 * @param callback the callback that should get called
	 * @throws OpenemsNamedException on error
	 */
	private static void onMatchString(Pattern pattern, String line,
			ThrowingConsumer<String, OpenemsNamedException> callback) throws OpenemsNamedException {
		Matcher matcher = pattern.matcher(line);
		if (matcher.find() && matcher.groupCount() > 0) {
			callback.accept(matcher.group(1));
		}
	}

	/**
	 * Helper function to match an Inet4Address in the configuration file.
	 * 
	 * @param pattern  the regular expression pattern
	 * @param line     the line of the file
	 * @param callback the callback that should get called
	 * @throws OpenemsNamedException on error
	 */
	private static void onMatchInet4Address(Pattern pattern, String line,
			ThrowingConsumer<Inet4Address, OpenemsNamedException> callback) throws OpenemsNamedException {
		onMatchString(pattern, line, property -> {
			try {
				callback.accept((Inet4Address) Inet4Address.getByName(property));
			} catch (UnknownHostException e) {
				throw new OpenemsException("Unable to parse IPv4 address [" + property + "]: " + e.getMessage());
			}
		});
	}

	/**
	 * Converts the NetworkInterface object to systemd-networkd file format.
	 * 
	 * @param iface the input network interface configuration
	 * @return a list of strings for writing it to a file
	 */
	private List<String> toFileFormat(NetworkInterface<?> iface) {
		List<String> result = new ArrayList<>();
		result.add("[Match]");
		result.add("Name=" + iface.getName());
		result.add("");

		result.add("[Network]");
		if (iface.getDhcp().isSetAndNotNull()) {
			result.add("DHCP=" + (iface.getDhcp().getValue() ? "yes" : "no"));
		}
		if (iface.getLinkLocalAddressing().isSetAndNotNull()) {
			result.add("LinkLocalAddressing=" + (iface.getLinkLocalAddressing().getValue() ? "yes" : "no"));
		}
		if (iface.getGateway().isSetAndNotNull()) {
			result.add("Gateway=" + iface.getGateway().getValue().getHostAddress());
		}
		if (iface.getDns().isSetAndNotNull()) {
			result.add("DNS=" + iface.getDns().getValue().getHostAddress());
		}
		if (iface.getAddresses().isSetAndNotNull()) {
			for (Inet4AddressWithNetmask address : iface.getAddresses().getValue()) {
				result.add("Address=" + address.toString());
			}
		}
		return result;
	}

	@Override
	public CompletableFuture<ExecuteSystemCommandResponse> handleExecuteCommandRequest(
			ExecuteSystemCommandRequest request) {
		CompletableFuture<ExecuteSystemCommandResponse> result = new CompletableFuture<>();

		try {
			Process proc;
			if (request.getUsername().isPresent() && request.getPassword().isPresent()) {
				// Authenticate with user and password
				proc = Runtime.getRuntime().exec(new String[] { //
						"/bin/bash", "-c", "--", //
						"echo " + request.getPassword().get() + " | " //
								+ " /usr/bin/sudo -Sk -p '' -u \"" + request.getUsername().get() + "\" -- " //
								+ request.getCommand() });
			} else if (request.getPassword().isPresent()) {
				// Authenticate with password (user must have 'sudo' permissions)
				proc = Runtime.getRuntime().exec(new String[] { //
						"/bin/bash", "-c", "--", //
						"echo " + request.getPassword().get() + " | " //
								+ " /usr/bin/sudo -Sk -p '' -- " //
								+ request.getCommand() });
			} else {
				// No authentication: run as current user
				proc = Runtime.getRuntime().exec(new String[] { //
						"/bin/bash", "-c", "--", request.getCommand() });
			}

			// get stdout and stderr
			CompletableFuture<List<String>> stdoutFuture = CompletableFuture
					.supplyAsync(new InputStreamToString(this.parent, request.getCommand(), proc.getInputStream()));
			CompletableFuture<List<String>> stderrFuture = CompletableFuture
					.supplyAsync(new InputStreamToString(this.parent, request.getCommand(), proc.getErrorStream()));

			if (request.isRunInBackground()) {
				/*
				 * run in background
				 */
				String[] stdout = new String[] { //
						"Command [" + request.getCommand() + "] executed in background...", //
						"Check system logs for more information." };
				result.complete(new ExecuteSystemCommandResponse(request.getId(), stdout, new String[0], 0));

			} else {
				/*
				 * run in foreground with timeout
				 */
				CompletableFuture.runAsync(() -> {
					List<String> stderr = new ArrayList<>();
					try {
						// apply command timeout
						if (!proc.waitFor(request.getTimeoutSeconds(), TimeUnit.SECONDS)) {
							stderr.add("Command [" + request.getCommand() + "] timed out.");
							proc.destroy();
						}

						List<String> stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
						stderr.addAll(stderrFuture.get(1, TimeUnit.SECONDS));
						result.complete(new ExecuteSystemCommandResponse(request.getId(), //
								stdout.toArray(new String[stdout.size()]), //
								stderr.toArray(new String[stderr.size()]), //
								proc.exitValue() //
						));

					} catch (Throwable e) {
						result.completeExceptionally(e);
					}
				});
			}
		} catch (IOException e) {
			result.completeExceptionally(e);
		}

		return result;
	}

	/**
	 * Asynchronously converts a InputStream to a String.
	 */
	private static class InputStreamToString implements Supplier<List<String>> {
		private final Logger log = LoggerFactory.getLogger(InputStreamToString.class);

		private final HostImpl parent;
		private final String command;
		private final InputStream stream;

		public InputStreamToString(HostImpl parent, String command, InputStream stream) {
			this.parent = parent;
			this.command = StringUtils.toShortString(command, 20);
			this.stream = stream;
		}

		@Override
		public List<String> get() {
			List<String> result = new ArrayList<>();
			BufferedReader reader = null;
			String line = null;
			try {
				reader = new BufferedReader(new InputStreamReader(this.stream));
				while ((line = reader.readLine()) != null) {
					result.add(line);
					this.parent.logInfo(this.log, "[" + this.command + "] " + line);
				}
			} catch (Throwable e) {
				result.add(e.getClass().getSimpleName() + ": " + line);
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						/* ignore */
					}
				}
			}
			return result;
		}
	}

	@Override
	public String getUsbConfiguration() throws OpenemsNamedException {
		try {
			if (!Files.exists(UDEV_PATH)) {
				return "";
			} else {
				List<String> lines = Files.readAllLines(UDEV_PATH, StandardCharsets.US_ASCII);
				return String.join("\n", lines);
			}
		} catch (IOException e) {
			throw new OpenemsException("Unable to read file [" + UDEV_PATH + "]: " + e.getMessage());
		}
	}

	private static final Pattern MATCH_NAME = Pattern.compile("^Name=([a-zA-Z0-9*]+)$");
	private static final Pattern NETWORK_ADDRESS = Pattern
			.compile("^Address=(" + NetworkConfiguration.PATTERN_INET4ADDRESS + "/\\d+)$");
	private static final Pattern NETWORK_DHCP = Pattern.compile("^DHCP=(\\w+)$");
	private static final Pattern NETWORK_LINK_LOCAL_ADDRESSING = Pattern.compile("^LinkLocalAddressing=(\\w+)$");
	private static final Pattern NETWORK_GATEWAY = Pattern
			.compile("^Gateway=(" + NetworkConfiguration.PATTERN_INET4ADDRESS + ")$");
	private static final Pattern NETWORK_DNS = Pattern
			.compile("^DNS=(" + NetworkConfiguration.PATTERN_INET4ADDRESS + ")$");

	/**
	 * Parses a Systemd-Networkd configuration file.
	 * 
	 * <p>
	 * See <a href=
	 * "https://man7.org/linux/man-pages/man5/systemd.network.5.html">systemd.network.5</a>
	 * man page
	 * 
	 * @param <A>        the type of the attachment
	 * @param lines      the lines to parse
	 * @param attachment to be added as an attachment to the
	 *                   {@link NetworkInterface}
	 * @return a {@link NetworkInterface}
	 * @throws OpenemsNamedException on error
	 */
	protected static <A> NetworkInterface<A> parseSystemdNetworkdConfigurationFile(List<String> lines, A attachment)
			throws OpenemsNamedException {
		Block currentBlock = Block.UNDEFINED;
		final AtomicReference<String> name = new AtomicReference<>();
		final AtomicReference<ConfigurationProperty<Boolean>> dhcp = new AtomicReference<>(
				ConfigurationProperty.asNotSet());
		final AtomicReference<ConfigurationProperty<Boolean>> linkLocalAddressing = new AtomicReference<>(
				ConfigurationProperty.asNotSet());
		final AtomicReference<ConfigurationProperty<Inet4Address>> gateway = new AtomicReference<>(
				ConfigurationProperty.asNotSet());
		final AtomicReference<ConfigurationProperty<Inet4Address>> dns = new AtomicReference<>(
				ConfigurationProperty.asNotSet());
		final AtomicReference<ConfigurationProperty<Set<Inet4AddressWithNetmask>>> addresses = new AtomicReference<>(
				ConfigurationProperty.asNotSet());

		for (String line : lines) {
			line = line.trim();
			/*
			 * Find current configuration block
			 */
			if (line.startsWith("[")) {
				switch (line) {
				case "[Match]":
					currentBlock = Block.MATCH;
					break;
				case "[Network]":
					currentBlock = Block.NETWORK;
					break;
				default:
					currentBlock = Block.UNDEFINED;
					break;
				}
				continue;
			}

			/*
			 * Parse Block
			 */
			switch (currentBlock) {
			case MATCH:
				onMatchString(MATCH_NAME, line, property -> {
					name.set(property);
				});
				break;
			case NETWORK:
				onMatchString(NETWORK_ADDRESS, line, property -> {
					Set<Inet4AddressWithNetmask> content = addresses.get().getValue();
					if (content == null) {
						content = new HashSet<>();
					}
					content.add(Inet4AddressWithNetmask.fromString(property));
					addresses.set(ConfigurationProperty.of(content));
				});
				onMatchString(NETWORK_DHCP, line, property -> {
					dhcp.set(ConfigurationProperty.of(property.toLowerCase().equals("yes")));
				});
				onMatchString(NETWORK_LINK_LOCAL_ADDRESSING, line, property -> {
					linkLocalAddressing.set(ConfigurationProperty.of(property.toLowerCase().equals("yes")));
				});
				onMatchInet4Address(NETWORK_GATEWAY, line, property -> {
					gateway.set(ConfigurationProperty.of(property));
				});
				onMatchInet4Address(NETWORK_DNS, line, property -> {
					dns.set(ConfigurationProperty.of(property));
				});
				break;
			case UNDEFINED:
				break;
			}
		}
		return new NetworkInterface<A>(name.get(), //
				dhcp.get(), linkLocalAddressing.get(), gateway.get(), dns.get(), addresses.get(), attachment);
	}

}

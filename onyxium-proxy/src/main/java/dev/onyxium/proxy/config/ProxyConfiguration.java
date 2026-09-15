package dev.onyxium.proxy.config;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import eu.okaeri.validator.OkaeriValidator;
import eu.okaeri.validator.annotation.Max;
import eu.okaeri.validator.annotation.Min;
import eu.okaeri.validator.annotation.NotBlank;
import eu.okaeri.validator.annotation.NotNull;
import eu.okaeri.validator.annotation.Size;

import dev.onyxium.forwarding.ForwardingToken;
import dev.onyxium.proxy.backend.BackendConfiguration;

@Header({ "Restart to apply changes.", "Environment placeholders: ${NAME} or ${NAME:default}." })
public final class ProxyConfiguration extends OkaeriConfig {

	@NotNull
	public Listener listener = new Listener();

	@Comment("Each backend needs the Onyxium plugin")
	@Size(min = 1, max = 128)
	@NotNull
	public Map<String, Backend> backends = new LinkedHashMap<>(Map.of("lobby", new Backend()));

	@Comment({ "Try these backends in order on login (15s timeout each).",
			"Fallback stops once a backend accepts the player." })
	@CustomKey("initial-servers")
	@Size(min = 1, max = 16)
	@NotNull
	public List<String> initialServers = List.of("lobby");

	@NotNull
	public Forwarding forwarding = new Forwarding();

	public static ProxyConfiguration loadConfiguration(Path path) {
		var config = ConfigManager.create(ProxyConfiguration.class, it -> it.configure(options -> {
			options.configurer(new YamlSnakeYamlConfigurer());
			options.bindFile(path);
			options.resolvePlaceholders();
		}));
		if (Files.notExists(path)) {
			var secret = new byte[32];
			new SecureRandom().nextBytes(secret);
			config.forwarding.secret = Base64.getEncoder().encodeToString(secret);
			config.saveDefaults();
			config.forwarding.secret = "";
		}
		var validator = OkaeriValidator.of();
		config.configure(options -> options.validator(entity -> validator.validate(entity).isEmpty()));
		config.load();
		config.validateBackends();
		new ForwardingToken(config.forwarding.secret);
		return config;
	}

	private void validateBackends() {
		for (var entry : backends.entrySet()) {
			if (entry.getKey() == null || !entry.getKey().matches("[a-zA-Z0-9_-]{1,64}") || entry.getValue() == null) {
				throw new IllegalArgumentException(
						"backends require non-null entries with names of 1-64 letters, digits, underscores or hyphens");
			}
			if (entry.getValue().address().isUnresolved())
				throw new IllegalArgumentException("Could not resolve a backend host");
		}
		if (initialServers.stream().anyMatch(name -> name == null || !backends.containsKey(name))
				|| initialServers.stream().distinct().count() != initialServers.size()) {
			throw new IllegalArgumentException("initial-servers must contain distinct names defined in backends");
		}
		if (bindAddress().isUnresolved())
			throw new IllegalArgumentException("Could not resolve listener.host");
	}

	public InetSocketAddress bindAddress() {
		return new InetSocketAddress(listener.host, listener.port);
	}

	public List<BackendConfiguration> initialBackends() {
		var tokens = new ForwardingToken(forwarding.secret);
		return initialServers.stream()
			.map(name -> new BackendConfiguration(backends.get(name).address(), tokens))
			.toList();
	}

	public String password() {
		return listener.password;
	}

	@Override
	public String toString() {
		return "ProxyConfiguration[backends=" + backends.keySet() + "]";
	}

	public static final class Listener extends OkaeriConfig {

		@Comment("0.0.0.0 binds all IPv4 interfaces.")
		@NotBlank
		@NotNull
		public String host = "0.0.0.0";

		@Comment("UDP port (1-65535).")
		@Min(1)
		@Max(65535)
		@NotNull
		public Integer port = 5520;

		@Comment("Leave empty to disable the password challenge.")
		@NotNull
		public String password = "";

	}

	public static final class Backend extends OkaeriConfig {

		@NotBlank
		@NotNull
		public String host = "127.0.0.1";

		@Comment("Backend UDP port (1-65535).")
		@Min(1)
		@Max(65535)
		@NotNull
		public Integer port = 5521;

		public InetSocketAddress address() {
			return new InetSocketAddress(host, port);
		}

	}

	public static final class Forwarding extends OkaeriConfig {

		@Comment({ "Generated on first run: Base64, at least 32 random bytes.",
				"Copy to ForwardingSecret in each backend's config.json. Keep private." })
		@NotBlank
		@NotNull
		public String secret = "";

	}

}

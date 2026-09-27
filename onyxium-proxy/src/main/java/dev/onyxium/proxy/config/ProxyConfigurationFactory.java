package dev.onyxium.proxy.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import eu.okaeri.validator.OkaeriValidator;

import dev.onyxium.forwarding.ForwardingToken;

public final class ProxyConfigurationFactory {

	private ProxyConfigurationFactory() {
	}

	/// Loads configuration with resolved placeholders. Creates defaults and a random
	/// forwarding secret only when the file does not exist.
	///
	/// @param path the YAML configuration file
	/// @return the validated configuration
	public static ProxyConfiguration load(Path path) {
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
		validateBackends(config);
		new ForwardingToken(config.forwarding.secret); // validate the token
		return config;
	}

	private static void validateBackends(ProxyConfiguration config) {
		for (var entry : config.backends.entrySet()) {
			if (entry.getKey() == null || !entry.getKey().matches("[a-zA-Z0-9_-]{1,64}") || entry.getValue() == null) {
				throw new IllegalArgumentException(
						"backends require non-null entries with names of 1-64 letters, digits, underscores or hyphens");
			}
			if (entry.getValue().address().isUnresolved())
				throw new IllegalArgumentException("Could not resolve a backend host");
		}
		if (config.initialServers.stream().anyMatch(name -> name == null || !config.backends.containsKey(name))
				|| config.initialServers.stream().distinct().count() != config.initialServers.size()) {
			throw new IllegalArgumentException("initial-servers must contain distinct names defined in backends");
		}
		if (config.bindAddress().isUnresolved())
			throw new IllegalArgumentException("Could not resolve listener.host");
	}

}

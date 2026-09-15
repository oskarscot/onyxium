package dev.onyxium.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import eu.okaeri.configs.exception.OkaeriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.auth.AuthConfiguration;
import dev.onyxium.proxy.auth.AuthenticationException;
import dev.onyxium.proxy.auth.HytaleDeviceLogin;
import dev.onyxium.proxy.config.ProxyConfiguration;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.LifecycleException;

public final class AppBootstrap {

	private static final Logger LOGGER = LoggerFactory.getLogger(AppBootstrap.class);

	private static final String USAGE = "Usage: onyxium-proxy [--config <path>] [--help]";

	// TODO: proper argument parsing
	void main(String... args) {
		if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
			System.out.println(USAGE);
			return;
		}
		try {
			var path = configPath(args).toAbsolutePath().normalize();
			var configuration = ProxyConfiguration.loadConfiguration(path);
			LOGGER.info("Loaded configuration from {}", path);
			AuthConfiguration credentials;
			try (var login = new HytaleDeviceLogin()) {
				credentials = login
					.login(prompt -> LOGGER.info("Log in to Hytale at {}?user_code={} (expires in {} seconds)",
							prompt.verificationUri(), URLEncoder.encode(prompt.userCode(), StandardCharsets.UTF_8),
							prompt.expiresIn()), AppBootstrap::selectProfile);
			}
			var proxy = new OnyxiumProxy(new NettyNetworkManager(configuration, credentials));
			Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop, "onyxium-shutdown"));
			LOGGER.info("Hytale authentication completed");
			proxy.start();
		}
		catch (OkaeriException exception) {
			LOGGER.error(
					"Could not load proxy configuration, check YAML types, validation constraints and environment placeholders");
			System.exit(1);
		}
		catch (IllegalArgumentException | AuthenticationException | LifecycleException exception) {
			LOGGER.error("{}", exception.getMessage());
			System.exit(1);
		}
	}

	private static UUID selectProfile(List<HytaleDeviceLogin.GameProfile> profiles) {
		for (var i = 0; i < profiles.size(); i++) {
			LOGGER.info("[{}] {} ({})", i + 1, profiles.get(i).username(), profiles.get(i).uuid());
		}
		var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		while (true) {
			LOGGER.info("Enter the number of the Hytale profile to use:");
			try {
				var line = input.readLine();
				if (line == null)
					throw new AuthenticationException(
							"Profile selection requires console input; restart with stdin available");
				var selected = Integer.parseInt(line.trim());
				if (selected >= 1 && selected <= profiles.size())
					return profiles.get(selected - 1).uuid();
			}
			catch (NumberFormatException exception) {
				LOGGER.warn("Enter a profile number from the list");
			}
			catch (IOException exception) {
				throw new AuthenticationException("Could not read profile selection from the console");
			}
		}
	}

	static Path configPath(String... args) {
		try {
			if (args.length == 0)
				return Path.of("onyxium.yml");
			if (args.length == 2 && args[0].equals("--config") && !args[1].isBlank())
				return Path.of(args[1]);
		}
		catch (InvalidPathException exception) {
			throw new IllegalArgumentException("Invalid configuration path");
		}
		throw new IllegalArgumentException(USAGE);
	}

}

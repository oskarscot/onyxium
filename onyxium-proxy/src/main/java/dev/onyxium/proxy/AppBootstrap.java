package dev.onyxium.proxy;
import eu.okaeri.configs.exception.OkaeriException;
import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import dev.onyxium.proxy.auth.AuthConfiguration;
import dev.onyxium.proxy.auth.AuthenticationException;
import dev.onyxium.proxy.auth.HytaleDeviceLogin;
import dev.onyxium.proxy.config.ProxyConfigurationFactory;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@Command(name = "onyxium-proxy", description = "Start the Onyxium Hytale proxy.", mixinStandardHelpOptions = true,
	versionProvider = BuildInfo.class)
public final class AppBootstrap implements Callable<Integer> {

	private static final Logger LOGGER = LoggerFactory.getLogger(AppBootstrap.class);

	Path configurationPath = Path.of("onyxium.yml");

	@Option(names = { "-c", "--config" }, paramLabel = "<path>", description = "Configuration file (default: onyxium.yml).")
	void configurationPath(String value) {
		if (value.isBlank())
			throw new IllegalArgumentException("Configuration path must not be blank");
		configurationPath = Path.of(value);
	}

	BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

	void main(String... args) {
        var exitCode = commandLine().execute(args);
        if (exitCode != 0)
            System.exit(exitCode);
    }

    CommandLine commandLine() {
        return new CommandLine(this);
    }

    @Override
    public Integer call() {
        var build = BuildInfo.load();
        LOGGER.info("Loading {}", build.display());
		try {
			var path = configurationPath.toAbsolutePath().normalize();
			var configuration = ProxyConfigurationFactory.load(path);

			LOGGER.info("Loaded configuration from {}", path);

			var credentials = authenticate(path);

			var proxy = new OnyxiumProxy(new NettyNetworkManager(configuration, credentials));

			Runtime.getRuntime().addShutdownHook(new Thread(proxy::shutdown, "onyxium-shutdown"));

			LOGGER.info("Hytale authentication completed");

			proxy.start();

			Thread.ofVirtual().name("onyxium-console").start(() -> proxy.console().read(input));
		}
		catch (OkaeriException exception) {
			LOGGER.error(
				"Could not load proxy configuration, check YAML types, validation constraints and environment placeholders");
			return 1;
		}

		catch (IllegalArgumentException | AuthenticationException | LifecycleException exception) {
			LOGGER.error("{}", exception.getMessage());
			return 1;
		}

        return 0;
    }

    private AuthConfiguration authenticate(Path configurationPath) {
        try (var login = new HytaleDeviceLogin(configurationPath.resolveSibling("onyxium-auth.json"))) {
            return login.login(prompt -> LOGGER.info(
                    "Log in to Hytale at {}?user_code={} (expires in {} seconds)",
                    prompt.verificationUri(), URLEncoder.encode(prompt.userCode(), StandardCharsets.UTF_8),
                    prompt.expiresIn()), this::selectProfile);
        }
    }

	UUID selectProfile(List<HytaleDeviceLogin.GameProfile> profiles) {
		for (var i = 0; i < profiles.size(); i++) {
			LOGGER.info("[{}] {} ({})", i + 1, profiles.get(i).username(), profiles.get(i).uuid());
		}

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

}

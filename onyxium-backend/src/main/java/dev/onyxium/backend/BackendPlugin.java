package dev.onyxium.backend;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import dev.onyxium.forwarding.ForwardingToken;

public class BackendPlugin extends JavaPlugin {

	private final Config<BackendPluginConfiguration> configuration = withConfig(BackendPluginConfiguration.CODEC);

	private boolean configurationFailed;

	private ForwardingFilter filter;

	public BackendPlugin(JavaPluginInit init) {
		super(init);
	}

	/// Keep the plugin enabled on malformed configuration so setup can install its
	/// rejecting filter instead of leaving the backend open to direct connections.
	@Override
	public CompletableFuture<Void> preLoad() {
		return super.preLoad().handle((ignored, failure) -> {
			configurationFailed = failure != null;
			return null;
		});
	}

	@Override
	protected void setup() {
		var path = getDataDirectory().resolve("config.json");
		ForwardingToken tokens = null;
		try {
			if (!configurationFailed) {
				if (Files.notExists(path))
					configuration.save().join();
				tokens = configuration.get().forwardingTokens();
			}
		}
		catch (CompletionException | IllegalArgumentException exception) {
			configurationFailed = true;
		}
		filter = new ForwardingFilter(tokens);
		PacketAdapters.registerInbound(filter);
		if (tokens != null)
			getLogger().atInfo().log("Onyxium forwarding configured from %s", path.toAbsolutePath());
		else
			getLogger().atSevere()
				.log("Invalid forwarding configuration in %s; set ForwardingSecret to match the proxy. Backend connections will be rejected",
						path.toAbsolutePath());
	}

	@Override
	protected void shutdown() {
		if (filter != null) {
			PacketAdapters.deregisterInbound(filter);
			filter = null;
		}
	}

}

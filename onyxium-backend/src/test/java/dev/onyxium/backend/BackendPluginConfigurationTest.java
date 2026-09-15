package dev.onyxium.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletionException;

import com.hypixel.hytale.server.core.util.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.onyxium.forwarding.ForwardingToken;

public class BackendPluginConfigurationTest {

	@Rule
	public final TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void hytaleDefaultsCanBeSavedButRejectConnectionsUntilConfigured() throws Exception {
		var directory = temporary.getRoot().toPath();
		var config = new Config<>(directory, "config", BackendPluginConfiguration.CODEC);
		config.load().join();
		config.save().join();
		assertTrue(Files.readString(directory.resolve("config.json")).contains("\"ForwardingSecret\""));
		assertThrows(IllegalArgumentException.class, () -> config.get().forwardingTokens());
	}

	@Test
	public void hytaleCodecLoadsTheProxysSecretWithoutRewritingTheFile() throws Exception {
		var directory = temporary.getRoot().toPath();
		var path = directory.resolve("config.json");
		var secret = Base64.getEncoder().encodeToString(new byte[32]);
		var content = "{\"ForwardingSecret\": \"" + secret + "\"}\n";
		Files.writeString(path, content);
		var config = new Config<>(directory, "config", BackendPluginConfiguration.CODEC);
		config.load().join();
		var backend = config.get().forwardingTokens();
		assertTrue(backend.verifyAcknowledgement("test", new ForwardingToken(secret).acknowledgement("test")));
		assertEquals(content, Files.readString(path));
	}

	@Test
	public void missingShortAndMalformedSecretsCannotAuthorizeForwarding() throws Exception {
		var directory = temporary.getRoot().toPath();
		var path = directory.resolve("config.json");
		for (var content : List.of("{}", "{\"ForwardingSecret\": \"invalid-secret\"}",
				"{\"ForwardingSecret\": \"" + Base64.getEncoder().encodeToString(new byte[16]) + "\"}")) {
			Files.writeString(path, content);
			var config = new Config<>(directory, "config", BackendPluginConfiguration.CODEC);
			config.load().join();
			assertThrows(IllegalArgumentException.class, () -> config.get().forwardingTokens());
			assertEquals(content, Files.readString(path));
		}
	}

	@Test
	public void malformedJsonFailsLoadingAndPreservesOperatorsFile() throws Exception {
		var directory = temporary.getRoot().toPath();
		var path = directory.resolve("config.json");
		Files.writeString(path, "{\"ForwardingSecret\": [");
		var config = new Config<>(directory, "config", BackendPluginConfiguration.CODEC);
		assertThrows(CompletionException.class, () -> config.load().join());
		assertEquals("{\"ForwardingSecret\": [", Files.readString(path));
	}

}

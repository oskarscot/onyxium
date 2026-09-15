package dev.onyxium.proxy.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import eu.okaeri.configs.exception.OkaeriException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProxyConfigurationTest {

	private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

	@Rule
	public final TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void createsCommentedDefaultsOnceWithoutAuthenticationSettings() throws Exception {
		var path = path();
		var config = ProxyConfiguration.loadConfiguration(path);
		var content = Files.readString(path);
		assertEquals(5520, config.bindAddress().getPort());
		assertEquals(5521, config.backends.get("lobby").address().getPort());
		assertEquals(List.of("lobby"), config.initialServers);
		assertTrue(content.contains("# Copy to ForwardingSecret"));
		assertFalse(content.contains("session-token:"));
		assertFalse(content.contains("identity-token:"));
		assertFalse(content.contains("audience:"));
		ProxyConfiguration.loadConfiguration(path);
		assertEquals(content, Files.readString(path));
		Files.writeString(path, "listener:\n  port: 5520\n");
		assertThrows(IllegalArgumentException.class, () -> ProxyConfiguration.loadConfiguration(path));
		assertEquals("listener:\n  port: 5520\n", Files.readString(path));
	}

	@Test
	public void resolvesPlaceholdersAndLoadsNamedBackendsWithoutRewritingFile() throws Exception {
		var path = path();
		var content = """
				listener:
				  port: '${onyxium.test.port:5522}'
				  password: 'a # password: ${onyxium.test.password}'
				backends:
				  lobby:
				    host: 127.0.0.1
				    port: 5521
				  survival:
				    host: 127.0.0.1
				    port: 5523
				initial-servers: [survival, lobby]
				forwarding:
				  secret: '${onyxium.test.secret}'
				""";
		Files.writeString(path, content);
		System.setProperty("onyxium.test.secret", SECRET);
		System.setProperty("onyxium.test.password", "private-password");
		try {
			var config = ProxyConfiguration.loadConfiguration(path);
			assertEquals(5522, config.bindAddress().getPort());
			assertEquals(2, config.backends.size());
			assertEquals(5523, config.backends.get("survival").address().getPort());
			assertEquals(List.of("survival", "lobby"), config.initialServers);
			assertEquals("a # password: private-password", config.password());
			assertFalse(config.toString().contains("private-password"));
			assertEquals(content, Files.readString(path));
			System.setProperty("onyxium.test.port", "65536");
			var exception = assertThrows(OkaeriException.class, () -> ProxyConfiguration.loadConfiguration(path));
		}
		finally {
			System.clearProperty("onyxium.test.secret");
			System.clearProperty("onyxium.test.password");
			System.clearProperty("onyxium.test.port");
		}
	}

	@Test
	public void resolvesRealEnvironmentValuesAndEscapedPlaceholders() throws Exception {
		var path = path();
		Files.writeString(path, "listener:\n  password: '${PATH}'\nforwarding:\n  secret: '" + SECRET + "'\n");
		assertEquals(System.getenv("PATH"), ProxyConfiguration.loadConfiguration(path).password());
		Files.writeString(path, "listener:\n  password: '$${LITERAL}'\nforwarding:\n  secret: '" + SECRET + "'\n");
		assertEquals("${LITERAL}", ProxyConfiguration.loadConfiguration(path).password());
	}

	@Test
	public void rejectsInvalidSettingsIncludingNestedBackendConstraints() throws Exception {
		var invalid = List.of("listener: {port: 0}", "listener: {port: 65536}", "listener: {host: '  '}",
				"listener: null", "listener: {password: null}", "backends: {}", "backends: {lobby: {port: -1}}",
				"backends: {lobby: {host: ''}}", "backends: {lobby: null}", "initial-servers: []",
				"initial-servers: [missing]", "initial-servers: [lobby, lobby]", "initial-servers: [null]",
				"listener: {port: '${ONYXIUM_TEST_UNDEFINED}'}");
		for (var settings : invalid) {
			var path = path();
			var content = settings + "\nforwarding:\n  secret: '" + SECRET + "'\n";
			Files.writeString(path, content);
			var exception = assertThrows(settings, RuntimeException.class,
					() -> ProxyConfiguration.loadConfiguration(path));
			assertTrue(settings, exception instanceof OkaeriException || exception instanceof IllegalArgumentException);
			assertEquals(content, Files.readString(path));
		}
	}

	@Test
	public void rejectsMalformedYamlAndSecretsWithoutRewritingFile() throws Exception {
		for (var content : List.of("listener: [\nsecret-value", "!!java.net.URL ['https://example.com']",
				"[not, a, mapping]", "forwarding: {secret: secret-value}")) {
			var path = path();
			Files.writeString(path, content);
			var exception = assertThrows(RuntimeException.class, () -> ProxyConfiguration.loadConfiguration(path));
			assertTrue(exception instanceof OkaeriException || exception instanceof IllegalArgumentException);
			assertEquals(content, Files.readString(path));
		}
	}

	private Path path() throws Exception {
		return temporary.newFolder().toPath().resolve("onyxium.yml");
	}

}

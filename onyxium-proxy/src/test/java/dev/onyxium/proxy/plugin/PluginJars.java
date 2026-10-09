package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import javax.tools.ToolProvider;

import com.nimbusds.jose.util.JSONObjectUtils;
import module java.base;

import dev.onyxium.command.Command;
import dev.onyxium.eventbus.EventBus;
import dev.onyxium.proxy.api.plugin.Plugin;

interface PluginJars {

	static String manifest(String id, String main, String... dependencies) {
		var metadata = new HashMap<String, Object>(Map.of("id", id, "version", "1.0", "main", main,
			"author", List.of("Oskar")));
		if (dependencies.length != 0) {
			metadata.put("dependencies", List.of(dependencies));
		}
		return JSONObjectUtils.toJSONString(metadata);
	}

	static byte[] utf8(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	static Path create(Path path, Map<String, String> sources, Map<String, byte[]> resources, Path... dependencies) throws IOException {
		Files.createDirectories(path.getParent());
		var workspace = Files.createTempDirectory(path.getParent(), ".compile-");
		var classes = Files.createDirectories(workspace.resolve("classes"));
		if (!sources.isEmpty()) {
			compile(workspace, classes, sources, dependencies);
		}
		try (var output = new JarOutputStream(Files.newOutputStream(path)); var files = Files.walk(classes)) {
			for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
				output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
				Files.copy(file, output);
				output.closeEntry();
			}
			for (var resource : resources.entrySet()) {
				output.putNextEntry(new JarEntry(resource.getKey()));
				output.write(resource.getValue());
				output.closeEntry();
			}
		}
		return path;
	}

	static void compile(Path workspace, Path classes, Map<String, String> sources, Path[] dependencies) throws IOException {
		var classpath = Stream.concat(Stream.of(Plugin.class, Command.class, EventBus.class).map(PluginJars::location),
			Stream.of(dependencies).map(Path::toString)).collect(Collectors.joining(File.pathSeparator));
		var arguments = new ArrayList<>(List.of("--release", "25", "-classpath", classpath, "-d", classes.toString()));
		for (var source : sources.entrySet()) {
			var file = workspace.resolve(source.getKey().replace('.', '/') + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue());
			arguments.add(file.toString());
		}
		var diagnostics = new ByteArrayOutputStream();
		var status = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics, arguments.toArray(String[]::new));
		assertThat(status).as(diagnostics.toString(StandardCharsets.UTF_8)).isZero();
	}

	static String location(Class<?> type) {
		try {
			return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
		}
		catch (URISyntaxException failure) {
			throw new IllegalArgumentException("Invalid fixture class path", failure);
		}
	}

	static String simplePlugin(String name, String id) {
		return """
			package fixture;
			import dev.onyxium.proxy.api.ProxyServer;
			import dev.onyxium.proxy.api.plugin.Plugin;
			public final class %s extends Plugin {
				public %s(ProxyServer proxy) { super(proxy); }
				public void load() { proxyServer().console().sendMessage("%s.load"); }
				public void enable() { proxyServer().console().sendMessage("%s.enable"); }
				public void disable() { proxyServer().console().sendMessage("%s.disable"); }
			}
			""".formatted(name, name, id, id, id);
	}

}

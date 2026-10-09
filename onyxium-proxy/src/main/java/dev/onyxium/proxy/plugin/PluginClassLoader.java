package dev.onyxium.proxy.plugin;

import module java.base;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.plugin.Plugin;
import dev.onyxium.proxy.lifecycle.LifecycleException;

final class PluginClassLoader extends URLClassLoader {

	static {
		registerAsParallelCapable();
	}

	PluginJar jar;

	List<PluginClassLoader> dependencies;

	PluginClassLoader(PluginJar jar, List<PluginClassLoader> dependencies) throws IOException {
		super("onyxium-plugin-" + jar.manifest().id(), new URL[] { jar.path().toUri().toURL() }, Plugin.class.getClassLoader());
		this.jar = jar;
		this.dependencies = List.copyOf(dependencies);
	}

	/// The inherited loadClass handles parent delegation and JVM class-loading locks.
	/// This fallback searches only the plugin's own JAR and declared dependency loaders.
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			return super.findClass(name);
		}
		catch (ClassNotFoundException missing) {
			for (var dependency : dependencies) {
				try {
					return dependency.loadClass(name);
				}
				catch (ClassNotFoundException failure) {
					missing.addSuppressed(failure);
				}
			}
			throw new ClassNotFoundException("Class '" + name + "' is unavailable to plugin '" + jar.manifest().id() + "'", missing);
		}
	}

	@Override
	public URL getResource(String name) {
		var resource = findResource(name);
		if (resource != null) {
			return resource;
		}
		for (var dependency : dependencies) {
			resource = dependency.getResource(name);
			if (resource != null) {
				return resource;
			}
		}
		return getParent().getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		var resources = new LinkedHashSet<>(Collections.list(findResources(name)));
		for (var dependency : dependencies) {
			resources.addAll(Collections.list(dependency.getResources(name)));
		}
		resources.addAll(Collections.list(getParent().getResources(name)));
		return Collections.enumeration(resources);
	}

	Plugin instantiate(ProxyServer proxy) {
		var thread = Thread.currentThread();
		var previous = thread.getContextClassLoader();
		thread.setContextClassLoader(this);
		try {
			var type = Class.forName(jar.manifest().main(), false, this).asSubclass(Plugin.class);
			if (type.getClassLoader() != this) {
				throw new IllegalArgumentException("Entry class must belong to the plugin's own JAR");
			}
			return type.getConstructor(ProxyServer.class).newInstance(proxy);
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
			throw new LifecycleException("Could not construct plugin '" + jar.manifest().id() + "' from " + jar.path()
				+ "; main must extend Plugin and have a public constructor accepting ProxyServer", failure);
		}
		finally {
			thread.setContextClassLoader(previous);
		}
	}

}

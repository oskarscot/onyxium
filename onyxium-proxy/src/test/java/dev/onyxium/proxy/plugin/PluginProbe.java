package dev.onyxium.proxy.plugin;

import module java.base;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.plugin.Plugin;
import dev.onyxium.proxy.api.plugin.PluginManifest;

final class PluginProbe extends Plugin {

	String id;

	List<String> callbacks;

	Set<Callback> failures = Set.of();

	Path dataDirectoryOnLoad;

	boolean dataDirectoryExistsOnLoad;

	PluginProbe(ProxyServer proxy, String id, List<String> callbacks) {
		super(proxy);
		this.id = id;
		this.callbacks = callbacks;
	}

	static PluginManifest manifest(String id, String... dependencies) {
		return new PluginManifest(id, "1.0", PluginProbe.class.getName(), List.of("Oskar"), List.of(dependencies));
	}

	@Override
	public void load() {
		dataDirectoryOnLoad = dataDirectory();
		dataDirectoryExistsOnLoad = Files.isDirectory(dataDirectoryOnLoad);
		record(Callback.LOAD);
	}

	@Override
	public void enable() {
		record(Callback.ENABLE);
	}

	@Override
	public void disable() {
		record(Callback.DISABLE);
	}

	void record(Callback callback) {
		var invocation = id + "." + callback.name().toLowerCase(Locale.ROOT);
		callbacks.add(invocation);
		if (failures.contains(callback)) {
			throw new AssertionError(invocation);
		}
	}

	enum Callback {
		LOAD, ENABLE, DISABLE
	}

}

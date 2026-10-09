package dev.onyxium.proxy.plugin;

import module java.base;
import org.jetbrains.annotations.ApiStatus;

import dev.onyxium.proxy.api.plugin.Plugin;
import dev.onyxium.proxy.api.plugin.PluginManifest;
import dev.onyxium.proxy.api.plugin.PluginService;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@ApiStatus.Internal
public final class PluginManager implements PluginService, Lifecycle {

	Path pluginsDirectory;

	Map<String, Registration> registrations = new LinkedHashMap<>();

	List<Registration> loadOrder = List.of();

	Phase phase = Phase.REGISTERING;

	public PluginManager() {
		this(Path.of("plugins"));
	}

	public PluginManager(Path pluginsDirectory) {
		this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory").toAbsolutePath().normalize();
	}

	/// Adds an instance and its metadata to the pending startup set without invoking
	/// callbacks. It is not visible through loaded-plugin queries until its load succeeds.
	/// Registration closes when startup begins. IDs and instance identities must be unique.
	///
	/// Dependencies may be registered afterward: the complete graph is checked at startup,
	/// so registration order does not determine callback order.
	/// The plugin's data path is assigned here; the directory is created before its load callback.
	public void register(PluginManifest manifest, Plugin plugin) {
		requirePhase(Phase.REGISTERING);
		Objects.requireNonNull(manifest, "manifest");
		Objects.requireNonNull(plugin, "plugin");
		if (registrations.containsKey(manifest.id())) {
			throw new IllegalArgumentException("Duplicate plugin ID '" + manifest.id() + "'");
		}
		if (registrations.values().stream().anyMatch(registration -> registration.plugin == plugin)) {
			throw new IllegalArgumentException("Plugin instance is already registered");
		}
		var dataDirectory = pluginsDirectory.resolve(manifest.id());
		registrations.put(manifest.id(), new Registration(manifest, plugin, dataDirectory));
	}

	@Override
	public Optional<Plugin> plugin(String id) {
		return loadedRegistration(id).map(registration -> registration.plugin);
	}

	@Override
	public List<Plugin> plugins() {
		return loadOrder.stream().filter(registration -> registration.loaded).map(registration -> registration.plugin).toList();
	}

	@Override
	public Optional<PluginManifest> manifest(String id) {
		return loadedRegistration(id).map(registration -> registration.manifest);
	}

	@Override
	public Path dataDirectory(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		return registrations.values().stream()
			.filter(registration -> registration.plugin == plugin)
			.map(registration -> registration.dataDirectory)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Plugin has not been registered"));
	}

	@Override
	public boolean isLoaded(String id) {
		return loadedRegistration(id).isPresent();
	}

	@Override
	public boolean isEnabled(String id) {
		return loadedRegistration(id).filter(registration -> registration.enabled).isPresent();
	}

	/// Validates dependencies, calls every load callback in dependency order, then
	/// enables every plugin in that order. No enable callback runs before all loads finish.
	/// The proxy starts its network listener only after this method succeeds.
	///
	/// A callback failure aborts startup and triggers reverse-order cleanup, including
	/// the failing instance and loaded instances that were never enabled. Cleanup errors
	/// are retained alongside the startup failure, and the manager becomes stopped.
	///
	/// @throws LifecycleException if required dependencies are missing, form a cycle, or a callback fails
	/// @throws IllegalStateException if startup has already begun or the manager has stopped
	@Override
	public void start() {
		requirePhase(Phase.REGISTERING);
		loadOrder = resolveOrder();
		phase = Phase.TRANSITIONING;
		try {
			for (var registration : loadOrder) {
				load(registration);
			}
			for (var registration : loadOrder) {
				enable(registration);
			}
			phase = Phase.RUNNING;
		}
		catch (RuntimeException | Error failure) {
			var startupFailure = new LifecycleException("Plugin startup failed: " + failure.getMessage(), failure);
			try {
				stopAll();
			}
			catch (LifecycleException cleanupFailure) {
				startupFailure.addSuppressed(cleanupFailure);
			}
			phase = Phase.STOPPED;
			throw startupFailure;
		}
	}

	@Override
	public void enable(String id) {
		requirePhase(Phase.RUNNING);
		var registration = requireLoaded(id);
		if (registration.enabled) {
			return;
		}
		requireEnabledDependencies(registration);
		phase = Phase.TRANSITIONING;
		try {
			enable(registration);
		}
		catch (RuntimeException | Error failure) {
			var enableFailure = new LifecycleException("Could not enable plugin '" + id + "'", failure);
			try {
				disable(registration);
			}
			catch (RuntimeException | Error cleanupFailure) {
				enableFailure.addSuppressed(cleanupFailure);
			}
			throw enableFailure;
		}
		finally {
			phase = Phase.RUNNING;
		}
	}

	@Override
	public void disable(String id) {
		requirePhase(Phase.RUNNING);
		var registration = requireLoaded(id);
		if (!registration.enabled) {
			return;
		}
		var dependents = loadOrder.stream()
			.filter(dependent -> dependent.enabled)
			.filter(dependent -> dependent.manifest.dependencies().contains(id))
			.map(dependent -> dependent.manifest.id())
			.toList();
		if (!dependents.isEmpty()) {
			throw new IllegalStateException("Plugin '" + id + "' is required by enabled plugins " + dependents);
		}
		phase = Phase.TRANSITIONING;
		try {
			disable(registration);
		}
		catch (RuntimeException | Error failure) {
			throw new LifecycleException("Could not disable plugin '" + id + "'", failure);
		}
		finally {
			phase = Phase.RUNNING;
		}
	}

	/// Cleans up in reverse dependency order and removes instances from loaded-plugin queries.
	/// The proxy stops its network listener before invoking this method. Cleanup includes
	/// loaded instances that never enabled, but skips an activation already cleaned up.
	///
	/// One plugin's cleanup failure does not prevent other plugins from being disabled.
	/// Failures are collected as suppressed exceptions. A stopped manager cannot restart;
	/// calling shutdown again does not repeat callbacks.
	///
	/// @throws LifecycleException if any cleanup callback fails
	/// @throws IllegalStateException if called during another lifecycle transition
	@Override
	public void shutdown() {
		if (phase == Phase.STOPPED) {
			return;
		}
		if (phase == Phase.TRANSITIONING) {
			throw new IllegalStateException("A plugin lifecycle transition is already running");
		}
		phase = Phase.TRANSITIONING;
		try {
			stopAll();
		}
		finally {
			phase = Phase.STOPPED;
		}
	}

	List<Registration> resolveOrder() {
		var ordered = new ArrayList<Registration>();
		var visiting = new LinkedHashSet<String>();
		var visited = new HashSet<String>();
		for (var registration : registrations.values()) {
			visit(registration, visiting, visited, ordered);
		}
		return List.copyOf(ordered);
	}

	void visit(Registration registration, Set<String> visiting, Set<String> visited, List<Registration> ordered) {
		var id = registration.manifest.id();
		if (visited.contains(id)) {
			return;
		}
		if (visiting.contains(id)) {
			throw new LifecycleException("Plugin dependency cycle: " + visiting + " -> " + id);
		}
		visiting.add(id);
		for (var dependencyId : registration.manifest.dependencies()) {
			var dependency = registrations.get(dependencyId);
			if (dependency == null) {
				throw new LifecycleException("Plugin '" + id + "' requires missing plugin '" + dependencyId + "'");
			}
			visit(dependency, visiting, visited, ordered);
		}
		visiting.remove(id);
		visited.add(id);
		ordered.add(registration);
	}

	void load(Registration registration) {
		try {
			Files.createDirectories(registration.dataDirectory);
			registration.needsCleanup = true;
			registration.plugin.load();
			registration.loaded = true;
		}
		catch (IOException failure) {
			throw new LifecycleException("Could not create data directory for plugin '" + registration.manifest.id() + "'", failure);
		}
		catch (RuntimeException | Error failure) {
			throw new LifecycleException("Could not load plugin '" + registration.manifest.id() + "'", failure);
		}
	}

	void enable(Registration registration) {
		registration.needsCleanup = true;
		try {
			registration.plugin.enable();
			registration.enabled = true;
		}
		catch (RuntimeException | Error failure) {
			throw new LifecycleException("Could not enable plugin '" + registration.manifest.id() + "'", failure);
		}
	}

	void requireEnabledDependencies(Registration registration) {
		var dependencies = registration.manifest.dependencies().stream().filter(id -> !isEnabled(id)).toList();
		if (!dependencies.isEmpty()) {
			throw new IllegalStateException("Plugin '" + registration.manifest.id() + "' requires enabled plugins " + dependencies);
		}
	}

	void disable(Registration registration) {
		registration.enabled = false;
		if (!registration.needsCleanup) {
			return;
		}
		try {
			registration.plugin.disable();
		}
		finally {
			registration.needsCleanup = false;
		}
	}

	void stopAll() {
		var shutdownFailure = new LifecycleException("One or more plugins failed to disable");
		for (var registration : loadOrder.reversed()) {
			try {
				disable(registration);
			}
			catch (RuntimeException | Error failure) {
				shutdownFailure.addSuppressed(new LifecycleException("Could not disable plugin '" + registration.manifest.id() + "'", failure));
			}
			finally {
				registration.loaded = false;
			}
		}
		if (shutdownFailure.getSuppressed().length != 0) {
			throw shutdownFailure;
		}
	}

	Optional<Registration> loadedRegistration(String id) {
		return Optional.ofNullable(registrations.get(Objects.requireNonNull(id, "id"))).filter(registration -> registration.loaded);
	}

	Registration requireLoaded(String id) {
		return loadedRegistration(id).orElseThrow(() -> new IllegalArgumentException("Plugin '" + id + "' is not loaded"));
	}

	void requirePhase(Phase expected) {
		if (phase != expected) {
			throw new IllegalStateException("Plugin manager is " + phase);
		}
	}

	enum Phase {
		REGISTERING, TRANSITIONING, RUNNING, STOPPED
	}

	static final class Registration {

		PluginManifest manifest;

		Plugin plugin;

		Path dataDirectory;

		boolean loaded;

		boolean enabled;

		boolean needsCleanup;

		Registration(PluginManifest manifest, Plugin plugin, Path dataDirectory) {
			this.manifest = manifest;
			this.plugin = plugin;
			this.dataDirectory = dataDirectory;
		}

	}

}

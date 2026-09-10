package dev.onyxium.backend;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class BackendPlugin extends JavaPlugin {

	public BackendPlugin(JavaPluginInit init) {
		super(init);
	}

	@Override
	protected void setup() {
		getLogger().atInfo().log("Onyxium backend enabled");
	}

}

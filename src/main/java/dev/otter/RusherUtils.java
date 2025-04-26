package dev.otter;

import dev.otter.hud.TNTCount;
import dev.otter.hud.WitherCount;
import dev.otter.module.AutoIgnite;
import dev.otter.module.AutoTNT;
import dev.otter.module.AutoTorch;
import dev.otter.module.autowither.AutoWither;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.feature.hud.HudElement;
import org.rusherhack.client.api.feature.module.IModule;
import org.rusherhack.client.api.plugin.Plugin;

public class RusherUtils extends Plugin {

	@Override
	public void onLoad() {
		// module
		register(new AutoTNT());
		register(new AutoIgnite());
		register(new AutoTorch());
		register(new AutoWither());

		// hud
		register(new TNTCount());
		register(new WitherCount());
	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("Unloaded GriefUtils");
	}

	private void register(Object o) {
		if (o instanceof IModule module) {
			RusherHackAPI.getModuleManager().registerFeature(module);
		} else if (o instanceof HudElement hudElement) {
			RusherHackAPI.getHudManager().registerFeature(hudElement);
		}
	}
}
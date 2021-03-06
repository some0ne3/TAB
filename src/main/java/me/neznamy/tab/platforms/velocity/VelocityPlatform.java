package me.neznamy.tab.platforms.velocity;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.VelocityTABLoadEvent;
import me.neznamy.tab.shared.Platform;
import me.neznamy.tab.shared.Shared;
import me.neznamy.tab.shared.config.Configs;
import me.neznamy.tab.shared.config.ConfigurationFile;
import me.neznamy.tab.shared.config.YamlConfigurationFile;
import me.neznamy.tab.shared.features.GlobalPlayerlist;
import me.neznamy.tab.shared.features.NameTag16;
import me.neznamy.tab.shared.features.PlaceholderManager;
import me.neznamy.tab.shared.features.bossbar.BossBar;
import me.neznamy.tab.shared.packets.UniversalPacketPlayOut;
import me.neznamy.tab.shared.permission.BungeePerms;
import me.neznamy.tab.shared.permission.LuckPerms;
import me.neznamy.tab.shared.permission.None;
import me.neznamy.tab.shared.permission.PermissionPlugin;
import me.neznamy.tab.shared.placeholders.PlayerPlaceholder;
import me.neznamy.tab.shared.placeholders.UniversalPlaceholderRegistry;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;

/**
 * Velocity implementation of Platform
 */
public class VelocityPlatform implements Platform {

	//instance of proxyserver
	private ProxyServer server;
	
	/**
	 * Constructs new instance with given parameter
	 * @param server - instance of proxyserver
	 */
	public VelocityPlatform(ProxyServer server) {
		this.server = server;
		UniversalPacketPlayOut.builder = new VelocityPacketBuilder();
	}
	
	@Override
	public PermissionPlugin detectPermissionPlugin() {
		if (server.getPluginManager().getPlugin("luckperms").isPresent()) {
			return new LuckPerms(server.getPluginManager().getPlugin("luckperms").get().getDescription().getVersion().get());
		} else if (server.getPluginManager().getPlugin("bungeeperms").isPresent()) {
			return new BungeePerms(server.getPluginManager().getPlugin("bungeeperms").get().getDescription().getVersion().get());
		} else {
			return new None();
		}
	}
	
	@Override
	public void loadFeatures() throws Exception{
		PlaceholderManager plm = new PlaceholderManager();
		Shared.featureManager.registerFeature("placeholders", plm);
		plm.addRegistry(new VelocityPlaceholderRegistry(server));
		plm.addRegistry(new UniversalPlaceholderRegistry());
		plm.registerPlaceholders();
		Shared.featureManager.registerFeature("injection", new VelocityPipelineInjector());
		if (Configs.config.getBoolean("change-nametag-prefix-suffix", true)) Shared.featureManager.registerFeature("nametag16", new NameTag16());
		loadUniversalFeatures();
		if (Configs.BossBarEnabled) 										Shared.featureManager.registerFeature("bossbar", new BossBar());
		if (Configs.config.getBoolean("global-playerlist.enabled", false)) 	Shared.featureManager.registerFeature("globalplayerlist", new GlobalPlayerlist());
		for (Player p : server.getAllPlayers()) {
			TabPlayer t = new VelocityTabPlayer(p);
			Shared.data.put(p.getUniqueId(), t);
		}
	}
	
	@Override
	public void sendConsoleMessage(String message, boolean translateColors) {
		server.getConsoleCommandSource().sendMessage(Identity.nil(), Component.text(translateColors ? PlaceholderManager.color(message): message));
	}

	@Override
	public void loadConfig() throws Exception {
		Configs.config = new YamlConfigurationFile(getClass().getClassLoader().getResourceAsStream("bungeeconfig.yml"), new File(getDataFolder(), "config.yml"), Arrays.asList("# Detailed explanation of all options available at https://github.com/NEZNAMY/TAB/wiki/config.yml", ""));
		Configs.serverAliases = Configs.config.getConfigurationSection("server-aliases");
	}
	
	@Override
	public void registerUnknownPlaceholder(String identifier) {
		if (identifier.contains("_")) {
			String plugin = identifier.split("_")[0].replace("%", "").toLowerCase();
			if (plugin.equals("some")) return;
			Shared.debug("Detected used PlaceholderAPI placeholder " + identifier);
			PlaceholderManager pl = (PlaceholderManager) Shared.featureManager.getFeature("placeholders");
			int cooldown = pl.defaultRefresh;
			if (pl.playerPlaceholderRefreshIntervals.containsKey(identifier)) cooldown = pl.playerPlaceholderRefreshIntervals.get(identifier);
			if (pl.serverPlaceholderRefreshIntervals.containsKey(identifier)) cooldown = pl.serverPlaceholderRefreshIntervals.get(identifier);
			((PlaceholderManager) Shared.featureManager.getFeature("placeholders")).registerPlaceholder(new PlayerPlaceholder(identifier, cooldown){
				public String get(TabPlayer p) {
					Main.plm.requestPlaceholder(p, identifier);
					return lastValue.get(p.getName());
				}
			});
			return;
		}
	}
	
	@Override
	public void convertConfig(ConfigurationFile config) {
		convertUniversalOptions(config);
		if (config.getName().equals("config.yml")) {
			if (config.getObject("global-playerlist") instanceof Boolean) {
				rename(config, "global-playerlist", "global-playerlist.enabled");
				config.set("global-playerlist.spy-servers", Arrays.asList("spyserver1", "spyserver2"));
				Map<String, List<String>> serverGroups = new HashMap<String, List<String>>();
				serverGroups.put("lobbies", Arrays.asList("lobby1", "lobby2"));
				serverGroups.put("group2", Arrays.asList("server1", "server2"));
				config.set("global-playerlist.server-groups", serverGroups);
				config.set("global-playerlist.display-others-as-spectators", false);
				Shared.print('2', "Converted old global-playerlist section to new one in config.yml.");
			}
			rename(config, "tablist-objective-value", "yellow-number-in-tablist");
		}
	}
	
	@Override
	public String getServerVersion() {
		return server.getVersion().getName() + " v" + server.getVersion().getVersion();
	}
	
	@Override
	public void suggestPlaceholders() {
		//bungee only
		suggestPlaceholderSwitch("%premiumvanish_bungeeplayercount%", "%canseeonline%");
		suggestPlaceholderSwitch("%bungee_total%", "%online%");
		for (RegisteredServer server : server.getAllServers()) {
			suggestPlaceholderSwitch("%bungee_" + server.getServerInfo().getName() + "%", "%online_" + server.getServerInfo().getName() + "%");
		}

		//both
		suggestPlaceholderSwitch("%cmi_user_ping%", "%ping%");
		suggestPlaceholderSwitch("%player_ping%", "%ping%");
		suggestPlaceholderSwitch("%viaversion_player_protocol_version%", "%player-version%");
		suggestPlaceholderSwitch("%player_name%", "%player%");
		suggestPlaceholderSwitch("%uperms_rank%", "%rank%");
	}

	@Override
	public String getSeparatorType() {
		return "server";
	}

	@Override
	public File getDataFolder() {
		return new File("plugins" + File.separatorChar + "TAB");
	}

	@Override
	public void callLoadEvent() {
		server.getEventManager().fire(new VelocityTABLoadEvent());
	}
}
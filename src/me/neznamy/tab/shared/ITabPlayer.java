package me.neznamy.tab.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import io.netty.channel.Channel;
import me.neznamy.tab.api.EnumProperty;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.platforms.bukkit.features.unlimitedtags.ArmorStand;
import me.neznamy.tab.platforms.bukkit.packets.PacketPlayOut;
import me.neznamy.tab.premium.Premium;
import me.neznamy.tab.premium.Scoreboard;
import me.neznamy.tab.premium.SortingType;
import me.neznamy.tab.shared.command.level1.PlayerCommand;
import me.neznamy.tab.shared.cpu.CPUFeature;
import me.neznamy.tab.shared.features.bossbar.BossBarLine;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.EnumGamemode;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.PlayerInfoData;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerListHeaderFooter;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardTeam;
import me.neznamy.tab.shared.packets.UniversalPacketPlayOut;
import me.neznamy.tab.shared.placeholders.Placeholders;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public abstract class ITabPlayer implements TabPlayer{

	public String name;
	public UUID uniqueId;
	public UUID tablistId;
	public String world;
	private String permissionGroup = "< Not Initialized Yet >";
	public String teamName;
	private String rank = "&7No Rank";

	public Map<String, Property> properties = new HashMap<String, Property>();
	public List<ArmorStand> armorStands = Collections.synchronizedList(new ArrayList<ArmorStand>());
	protected ProtocolVersion version = ProtocolVersion.SERVER_VERSION;
	public Channel channel;
	public boolean nameTagVisible = true;
	public boolean bossbarVisible;
	private PlayerInfoData infoData;

	public boolean disabledHeaderFooter;
	public boolean disabledTablistNames;
	public boolean disabledNametag;
	public boolean disabledTablistObjective;
	public boolean disabledBossbar;
	public boolean disabledBelowname;

	private Scoreboard activeScoreboard;
	public boolean hiddenScoreboard;
	public boolean previewingNametag;
	public List<BossBarLine> activeBossBars = new ArrayList<BossBarLine>();
	public boolean lastCollision;
	public boolean lastVisibility;
	public boolean onJoinFinished;
	public boolean hiddenNametag;

	public void init() {
		updateGroupIfNeeded(false);
		updateAll();
		updateDisabledWorlds(getWorldName());
		infoData = new PlayerInfoData(name, tablistId, null, 0, EnumGamemode.CREATIVE, null);
	}

	//bukkit only
	public boolean hasInvisibility() {
		return false;
	}

	//per-type
	public abstract String getGroupFromPermPlugin();

	public abstract String[] getGroupsFromPermPlugin();

	public abstract boolean hasPermission(String permission);

	public abstract long getPing();

	public abstract void sendPacket(Object nmsPacket);

	public abstract void sendMessage(String message);

	public abstract void sendRawMessage(String message);

	public abstract Object getSkin();

	public org.bukkit.entity.Player getBukkitEntity() {
		throw new IllegalStateException("Wrong platform");
	}

	public ProxiedPlayer getBungeeEntity() {
		throw new IllegalStateException("Wrong platform");
	}

	public com.velocitypowered.api.proxy.Player getVelocityEntity() {
		throw new IllegalStateException("Wrong platform");
	}

	public boolean getTeamPush() {
		return Configs.getCollisionRule(world);
	}

	public String getName() {
		return name;
	}

	public UUID getUniqueId() {
		return uniqueId;
	}

	public UUID getTablistId() {
		return tablistId;
	}

	public ProtocolVersion getVersion() {
		return version;
	}
	public String getRank() {
		return rank;
	}

	public boolean isStaff() {
		return hasPermission("tab.staff");
	}

	public void setActiveScoreboard(Scoreboard board) {
		activeScoreboard = board;
	}

	public Scoreboard getActiveScoreboard() {
		return activeScoreboard;
	}

	public String getWorldName() {
		return world;
	}

	public List<ArmorStand> getArmorStands() {
		return armorStands;
	}

	public PlayerInfoData getInfoData() {
		return infoData;
	}

	public String setProperty(String identifier, String rawValue, String source) {
		Property p = properties.get(identifier);
		if (p == null) {
			properties.put(identifier, new Property(this, rawValue, source));
		} else {
			p.changeRawValue(rawValue);
			p.setSource(source);
		}
		return rawValue;
	}

	public void updateTeam() {
		if (disabledNametag) return;
		if (teamName == null) return; //player not loaded yet
		String newName = SortingType.INSTANCE.getTeamName(this);
		if (teamName.equals(newName)) {
			updateTeamData();
		} else {
			unregisterTeam();
			teamName = newName;
			registerTeam();
		}
	}

	private boolean getTeamVisibility() {
		if (hiddenNametag || Configs.SECRET_invisible_nametags) return false;
		return !Shared.features.containsKey("nametagx") && nameTagVisible;
	}

	public String getGroup() {
		return permissionGroup;
	}

	public void updateGroupIfNeeded(boolean updateDataIfChanged) {
		String newGroup = "null";
		if (Configs.groupsByPermissions) {
			for (Object group : Configs.primaryGroupFindingList) {
				if (hasPermission("tab.group." + group)) {
					newGroup = String.valueOf(group);
					break;
				}
			}
			if (newGroup.equals("null")) {
				Shared.errorManager.oneTimeConsoleError("Player " + name + " does not have any group permission while assign-groups-by-permissions is enabled! Did you forget to add his group to primary-group-finding-list?");
			}
		} else {
			if (Configs.usePrimaryGroup) {
				newGroup = getGroupFromPermPlugin();
			} else {
				String[] playerGroups = getGroupsFromPermPlugin();
				if (playerGroups != null && playerGroups.length > 0) {
					loop:
						for (Object entry : Configs.primaryGroupFindingList) {
							for (String playerGroup : playerGroups) {
								if (playerGroup.equalsIgnoreCase(entry + "")) {
									newGroup = playerGroup;
									break loop;
								}
							}
						}
				if (playerGroups[0] != null && newGroup.equals("null")) newGroup = playerGroups[0];
				}
			}
		}
		if (!permissionGroup.equals(newGroup)) {
			permissionGroup = newGroup;
			if (updateDataIfChanged) {
				updateAll();
				forceUpdateDisplay();
			}
		}
	}

	public void updateAll() {
		updateProperty("tabprefix");
		updateProperty("tagprefix");
		updateProperty("tabsuffix");
		updateProperty("tagsuffix");
		updateProperty("customtabname");
		if (properties.get("customtabname").getCurrentRawValue().length() == 0) setProperty("customtabname", getName(), "None");
		updateProperty("customtagname");
		if (properties.get("customtagname").getCurrentRawValue().length() == 0) setProperty("customtagname", getName(), "None");
		setProperty("nametag", properties.get("tagprefix").getCurrentRawValue() + properties.get("customtagname").getCurrentRawValue() + properties.get("tagsuffix").getCurrentRawValue(), null);
		for (String property : Premium.dynamicLines) {
			if (!property.equals("nametag")) updateProperty(property);
		}
		for (String property : Premium.staticLines.keySet()) {
			if (!property.equals("nametag")) updateProperty(property);
		}
		rank = String.valueOf(Configs.rankAliases.get("_OTHER_"));
		if (rank.equals("null")) rank = null;
		for (Entry<Object, Object> entry : Configs.rankAliases.entrySet()) {
			if (String.valueOf(entry.getKey()).equalsIgnoreCase(permissionGroup)) {
				rank = String.valueOf(entry.getValue());
				break;
			}
		}
		if (rank == null) rank = permissionGroup;
		updateRawHeaderAndFooter();
	}

	private void updateProperty(String property) {
		String playerGroupFromConfig = permissionGroup.replace(".", "@#@");
		String worldGroup = getWorldGroupOf(getWorldName());
		String value;
		if ((value = Configs.config.getString("per-" + Shared.separatorType + "-settings." + worldGroup + ".Users." + getName() + "." + property)) != null) {
			setProperty(property, value, "Player: " + getName() + ", " + Shared.separatorType + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("per-" + Shared.separatorType + "-settings." + worldGroup + ".Users." + getUniqueId().toString() + "." + property)) != null) {
			setProperty(property, value, "PlayerUUID: " + getName() + ", " + Shared.separatorType + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("Users." + getName() + "." + property)) != null) {
			setProperty(property, value, "Player: " + getName());
			return;
		}
		if ((value = Configs.config.getString("Users." + getUniqueId().toString() + "." + property)) != null) {
			setProperty(property, value, "PlayerUUID: " + getName());
			return;
		}
		if ((value = Configs.config.getString("per-" + Shared.separatorType + "-settings." + worldGroup + ".Groups." + playerGroupFromConfig + "." + property)) != null) {
			setProperty(property, value, "Group: " + permissionGroup + ", " + Shared.separatorType + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("per-" + Shared.separatorType + "-settings." + worldGroup + ".Groups._OTHER_." + property)) != null) {
			setProperty(property, value, "Group: _OTHER_," + Shared.separatorType + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("Groups." + playerGroupFromConfig + "." + property)) != null) {
			setProperty(property, value, "Group: " + permissionGroup);
			return;
		}
		if ((value = Configs.config.getString("Groups._OTHER_." + property)) != null) {
			setProperty(property, value, "Group: _OTHER_");
			return;
		}
		setProperty(property, "", "None");
	}

	private void updateRawHeaderAndFooter() {
		updateRawValue("header");
		updateRawValue("footer");
	}

	private void updateRawValue(String name) {
		String worldGroup = getWorldGroupOf(getWorldName());
		StringBuilder rawValue = new StringBuilder();
		List<String> lines = Configs.config.getStringList("per-" + Shared.separatorType + "-settings." + worldGroup + ".Users." + getName() + "." + name);
		if (lines == null) lines = Configs.config.getStringList("per-" + Shared.separatorType + "-settings." + worldGroup + ".Users." + getUniqueId().toString() + "." + name);
		if (lines == null) lines = Configs.config.getStringList("Users." + getName() + "." + name);
		if (lines == null) lines = Configs.config.getStringList("Users." + getUniqueId().toString() + "." + name);
		if (lines == null) lines = Configs.config.getStringList("per-" + Shared.separatorType + "-settings." + worldGroup + ".Groups." + permissionGroup + "." + name);
		if (lines == null) lines = Configs.config.getStringList("per-" + Shared.separatorType + "-settings." + worldGroup + "." + name);
		if (lines == null) lines = Configs.config.getStringList("Groups." + permissionGroup + "." + name);
		if (lines == null) lines = Configs.config.getStringList(name);
		if (lines == null) lines = new ArrayList<String>();
		int i = 0;
		for (String line : lines) {
			if (++i > 1) rawValue.append("\n" + Placeholders.colorChar + "r");
			rawValue.append(line);
		}
		setProperty(name, rawValue.toString(), null);
	}

	@SuppressWarnings("unchecked")
	private String getWorldGroupOf(String world) {
		Map<String, Object> worlds = Configs.config.getConfigurationSection("per-" + Shared.separatorType + "-settings");
		if (worlds.isEmpty()) return world;
		for (String worldGroup : worlds.keySet()) {
			for (String localWorld : worldGroup.split(Configs.SECRET_multiWorldSeparator)) {
				if (localWorld.equalsIgnoreCase(world)) return worldGroup;
			}
		}
		return world;
	}

	public void updateTeamData() {
		if (disabledNametag) return;
		Property tagprefix = properties.get("tagprefix");
		Property tagsuffix = properties.get("tagsuffix");
		boolean collision = getTeamPush();
		boolean visible = getTeamVisibility();
		for (ITabPlayer viewer : Shared.getPlayers()) {
			String currentPrefix = tagprefix.getFormat(viewer);
			String currentSuffix = tagsuffix.getFormat(viewer);
			PacketAPI.updateScoreboardTeamPrefixSuffix(viewer, teamName, currentPrefix, currentSuffix, visible, collision);
		}
	}

	public void registerTeam() {
		Property tagprefix = properties.get("tagprefix");
		Property tagsuffix = properties.get("tagsuffix");
		for (ITabPlayer viewer : Shared.getPlayers()) {
			String currentPrefix = tagprefix.getFormat(viewer);
			String currentSuffix = tagsuffix.getFormat(viewer);
			PacketAPI.registerScoreboardTeam(viewer, teamName, currentPrefix, currentSuffix, lastVisibility = getTeamVisibility(), lastCollision = getTeamPush(), Arrays.asList(getName()), null);
		}
	}

	public void registerTeam(ITabPlayer viewer) {
		Property tagprefix = properties.get("tagprefix");
		Property tagsuffix = properties.get("tagsuffix");
		String replacedPrefix = tagprefix.getFormat(viewer);
		String replacedSuffix = tagsuffix.getFormat(viewer);
		PacketAPI.registerScoreboardTeam(viewer, teamName, replacedPrefix, replacedSuffix, getTeamVisibility(), getTeamPush(), Arrays.asList(getName()), null);
	}

	public void unregisterTeam() {
		Object packet = PacketPlayOutScoreboardTeam.REMOVE_TEAM(teamName).setTeamOptions(69).build(ProtocolVersion.SERVER_VERSION);
		for (ITabPlayer viewer : Shared.getPlayers()) {
			viewer.sendPacket(packet);
		}
	}

	public void unregisterTeam(ITabPlayer viewer) {
		viewer.sendCustomPacket(PacketPlayOutScoreboardTeam.REMOVE_TEAM(teamName).setTeamOptions(69));
	}

	private void updateDisabledWorlds(String world) {
		disabledHeaderFooter = isDisabledWorld(Configs.disabledHeaderFooter, world);
		disabledTablistNames = isDisabledWorld(Configs.disabledTablistNames, world);
		disabledNametag = isDisabledWorld(Configs.disabledNametag, world);
		disabledTablistObjective = isDisabledWorld(Configs.disabledTablistObjective, world);
		disabledBossbar = isDisabledWorld(Configs.disabledBossbar, world);
		disabledBelowname = isDisabledWorld(Configs.disabledBelowname, world);
	}
	public boolean isDisabledWorld(List<String> disabledWorlds, String world) {
		if (disabledWorlds == null) return false;
		if (disabledWorlds.contains("WHITELIST")) {
			for (String enabled : disabledWorlds) {
				if (enabled != null && enabled.equals(world)) return false;
			}
			return true;
		} else {
			for (String disabled : disabledWorlds) {
				if (disabled != null && disabled.equals(world)) return true;
			}
			return false;
		}
	}

	public void onWorldChange(String from, String to) {
		updateDisabledWorlds(to);
		updateGroupIfNeeded(false);
		updateAll();
		ITabPlayer player = this;
		Shared.featureCpu.runMeasuredTask("processing world change", CPUFeature.WORLD_SWITCH, new Runnable() {

			@Override
			public void run() {
				Shared.worldChangeListeners.forEach(f -> f.onWorldChange(player, from, to));
			}
		});
	}

	public void sendCustomPacket(UniversalPacketPlayOut packet) {
		sendPacket(packet.build(getVersion()));
	}
	public void sendCustomBukkitPacket(PacketPlayOut packet) {
		try {
			sendPacket(packet.toNMS(getVersion()));
		} catch (Throwable e) {
			Shared.errorManager.printError("An error occurred when creating " + packet.getClass().getSimpleName(), e);
		}
	}
	public void forceUpdateDisplay() {
		Shared.refreshables.forEach(r -> r.refresh(this, true));
	}
	
	
	/*
	 *  Implementing interface
	 */
	
	public void setValueTemporarily(EnumProperty type, String value) {
		Placeholders.checkForRegistration(value);
		properties.get(type.toString()).setTemporaryValue(value);
		if (Shared.features.containsKey("nametagx") && type.toString().contains("tag")) {
			setProperty("nametag",properties.get("tagprefix").getCurrentRawValue() + properties.get("customtagname").getCurrentRawValue() + properties.get("tagsuffix").getCurrentRawValue(), null);
		}
		forceUpdateDisplay();
	}
	public void setValuePermanently(EnumProperty type, String value) {
		Placeholders.checkForRegistration(value);
		properties.get(type.toString()).changeRawValue(value);
		((PlayerCommand)Shared.command.subcommands.get("player")).savePlayer(null, getName(), type.toString(), value);
		if (Shared.features.containsKey("nametagx") && type.toString().contains("tag")) {
			setProperty("nametag", properties.get("tagprefix").getCurrentRawValue() + properties.get("customtagname").getCurrentRawValue() + properties.get("tagsuffix").getCurrentRawValue(), null);
		}
		forceUpdateDisplay();
	}
	public String getTemporaryValue(EnumProperty type) {
		return properties.get(type.toString()).getTemporaryValue();
	}
	public boolean hasTemporaryValue(EnumProperty type) {
		return getTemporaryValue(type) != null;
	}
	public void removeTemporaryValue(EnumProperty type) {
		setValueTemporarily(type, null);
	}
	public String getOriginalValue(EnumProperty type) {
		return properties.get(type.toString()).getOriginalRawValue();
	}
	public void sendHeaderFooter(String header, String footer) {
		sendCustomPacket(new PacketPlayOutPlayerListHeaderFooter(header, footer));
	}
	public void hideNametag() {
		hiddenNametag = true;
		updateTeamData();
	}
	public void showNametag() {
		hiddenNametag = false;
		updateTeamData();
	}
	public boolean hasHiddenNametag() {
		return hiddenNametag;
	}
}
package net.enelson.astract.regionscleaner;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;

public class ARegionsCleaner extends JavaPlugin implements CommandExecutor {
	private File file;
	private YamlConfiguration config;
	private BukkitTask task;
	private Essentials essentials;
	private static Plugin plugin;

	public void onEnable() {
		this.essentials = (Essentials)Bukkit.getPluginManager().getPlugin("Essentials");
		if(this.essentials == null) {
			Bukkit.getLogger().warning("Essentials in not installed! The plugin has been disabled.");
			return;
		}
		plugin = this;
		this.getConfigs();
		this.startTasker();
		this.getCommand("aregionscleaner").setExecutor(this);
	}

	public void onDisable() {
		if (this.task != null && !this.task.isCancelled())
			this.task.cancel();
	}

	private void startTasker() {
		this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
			@Override
			public void run() {
				for (World world : Bukkit.getWorlds()) {
					if (config.getList("filter.ignore-worlds").contains(world.getName()))
						continue;
					
					List<ProtectedRegion> clean = new ArrayList<>();
					RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
					RegionManager regions = container.get(BukkitAdapter.adapt(world));
					regions.getRegions().forEach((k, region) -> {
						if(k.equals("__global__") || config.getList("filter.ignore-regions").contains(k)
								|| (config.getBoolean("filter.ignore-without-owners") && region.getOwners().size()==0))
							return;
						

						for (UUID uuid : region.getOwners().getUniqueIds()) { 
							if(!isWasLangAgo(uuid)) return;
						}
						
						for (UUID uuid : region.getMembers().getUniqueIds()) { 
							if(!isWasLangAgo(uuid)) return;
						}
						
						clean.add(region);
					});
					
					Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
						@Override
						public void run() {
							for(ProtectedRegion region : clean) {
								regions.removeRegion(region.getId());
								if(config.getBoolean("log-in-console"))
									Bukkit.getLogger().info("Region \"" + region.getId() + "\" has been deleted.");
							}
						}
					}, 1);
				}
			}
		}, 20 * 5, 20 * this.config.getInt("period"));
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (sender.isOp() && args.length != 0 && args[0].equalsIgnoreCase("reload")) {
			if (this.task != null && !this.task.isCancelled())
				this.task.cancel();
			this.getConfigs();
			this.startTasker();
			Bukkit.getLogger().info("The plugin has been reloaded.");
		}
		return true;
	}

	private void getConfigs() {
		this.file = new File(getDataFolder(), "config.yml");
		if (!this.file.exists())
			saveResource("config.yml", true);
		this.config = YamlConfiguration.loadConfiguration(this.file);
	}

	public boolean isWasLangAgo(UUID uuid) {
		if (this.essentials == null)
			return false;

		User user = this.essentials.getUser(uuid);
		if (user != null) {
	        Date lastLoginDate = new Date(user.getLastLogin());
	        Date currentDate = new Date();

	        long differenceInMillis = currentDate.getTime() - lastLoginDate.getTime();
	        long differenceInDays = TimeUnit.DAYS.convert(differenceInMillis, TimeUnit.MILLISECONDS);

	        return differenceInDays > this.config.getInt("expire-time");
		}
		return false;
	}
}

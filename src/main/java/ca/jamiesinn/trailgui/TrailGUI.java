package ca.jamiesinn.trailgui;

import ca.jamiesinn.trailgui.commands.CommandTrail;
import ca.jamiesinn.trailgui.commands.CommandTrailGUI;
import ca.jamiesinn.trailgui.commands.CommandTrails;
import ca.jamiesinn.trailgui.files.Updater;
import ca.jamiesinn.trailgui.files.Userdata;
import ca.jamiesinn.trailgui.sql.SQLManager;
import ca.jamiesinn.trailgui.trails.*;
import com.earth2me.essentials.IEssentials;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrailGUI
        extends JavaPlugin
{
    public static TrailGUI plugin;
    public static String prefix;
    public static boolean removeTrailOnPlayerHit;
    public static boolean oneTrailAtATime;
    public static int maxTrails;
    public static List<String> disabledWorlds;
    public static Map<UUID, List<Trail>> enabledTrails = new HashMap<UUID, List<Trail>>();
    public static Map<String, Trail> trailTypes = new HashMap<String, Trail>();
    public static IEssentials ess;
    private static SQLManager sqlManager;

    public static TrailGUI getPlugin()
    {
        return plugin;
    }

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getCommand("trail").setExecutor(new CommandTrail(this));
        getCommand("trails").setExecutor(new CommandTrails(this));
        getCommand("trailgui").setExecutor(new CommandTrailGUI(this));
        plugin = this;


        if (getConfig().getBoolean("metrics"))
        {
            try
            {
                Metrics metrics = new Metrics(this);
                metrics.start();
            }
            catch (IOException ignored)
            {
            }
        }

        if (getConfig().getBoolean("updater"))
        {
            try
            {
                Updater updater = new Updater(plugin);
                updater.check();
            }
			catch (IOException e)
            {
                e.printStackTrace();
            }
			catch (NumberFormatException nfe)
			{
            	getLogger().severe("Updater can't establish connection");
            	nfe.printStackTrace();
            }
        }
        if (getConfig().getBoolean("mysql"))
        {
            try
            {
                SQLManager sql = new SQLManager(getConfig().getString("mysql-conn.host"),
                        getConfig().getInt("mysql-conn.port"),
                        getConfig().getString("mysql-conn.database"),
                        getConfig().getString("mysql-conn.user"),
                        getConfig().getString("mysql-conn.pass"));
                sqlManager = sql;
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }

        load();
    }

    private void hookEss()
    {
        final PluginManager pm = this.getServer().getPluginManager();
        final Plugin pl = pm.getPlugin("Essentials");
        if (plugin == null || !plugin.isEnabled())
        {
            getLogger().warning("Couldn't hook Essentials - Not using vanish hooks");
            return;
        }
        ess = (IEssentials) pl;
        getLogger().info("Hooked Essentials Successfully");
    }

    private void load()
    {
        reloadConfig();
        maxTrails = getConfig().getInt("maxActiveTrails");
        oneTrailAtATime = getConfig().getBoolean("oneTrailAtATime", false);
        prefix = getConfig().getString("prefix").replaceAll("&", "\u00A7");
        if (prefix == null)
        {
            getLogger().info(ChatColor.RED + "Warning - You have either no value for the prefix - or you have an outdated config. Please update it.");
            prefix = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "TrailGUI" + ChatColor.DARK_GRAY + "] ";
        }
        removeTrailOnPlayerHit = getConfig().getBoolean("removeTrailOnPlayerHit", false);
        disabledWorlds = getConfig().getStringList("disabledWorlds");
        new Userdata().loadConfig();
        loadTrails();
        Util.restoreTrails();
        hookEss();
    }

    public void reload()
    {
        trailTypes.clear();
        enabledTrails.clear();
        load();
    }

    private void loadTrails()
    {
        if (getConfig().isConfigurationSection("trails"))
        {
            ConfigurationSection section = getConfig().getConfigurationSection("trails");
            for (String key : section.getKeys(false))
            {
                if (section.isConfigurationSection(key))
                {
                    ConfigurationSection trailTypeSection = section.getConfigurationSection(key);
                    try
                    {
                        if (trailTypeSection.getString("type").equalsIgnoreCase("ITEM_CRACK"))
                        {
                            trailTypes.put(trailTypeSection.getName(), new ItemTrail(trailTypeSection));
                        }
                        else if (trailTypeSection.getString("type").equalsIgnoreCase("BLOCK_CRACK"))
                        {
                            trailTypes.put(trailTypeSection.getName(), new BlockTrail(trailTypeSection));
                        }
                        else if (trailTypeSection.getBoolean("is_effect", false))
                        {
                            trailTypes.put(trailTypeSection.getName(), new EffectTrail(trailTypeSection));
                        }
                        else
                        {
                            trailTypes.put(trailTypeSection.getName(), new NormalTrail(trailTypeSection));
                        }
                    }
                    catch (Exception ex)
                    {
                        getLogger().warning("Failed to load '" + trailTypeSection.getName() + "'. Error: " + ex.getMessage());
                    }

                }
            }
        }
    }

    @Override
    public void onDisable()
    {
        Util.saveTrails();
        if (getConfig().getBoolean("mysql"))
            sqlManager.disconnect();
    }

    public static SQLManager getSqlManager()
    {
        return sqlManager;
    }
}
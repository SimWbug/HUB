package fr.cleangang.hub;

import fr.cleangang.hub.command.HubCommand;
import fr.cleangang.hub.command.WorldCommand;
import fr.cleangang.hub.gui.GamesMenu;
import fr.cleangang.hub.gui.MapMenu;
import fr.cleangang.hub.gui.MenuListener;
import fr.cleangang.hub.listener.BookListener;
import fr.cleangang.hub.map.MapBook;
import fr.cleangang.hub.map.PointStore;
import fr.cleangang.hub.util.Teleporter;
import fr.cleangang.hub.util.Text;
import fr.cleangang.hub.world.BackStore;
import fr.cleangang.hub.world.WorldService;
import fr.cleangang.hub.world.listener.FlagListener;
import fr.cleangang.hub.world.listener.PlayerWorldListener;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public final class CleanGangHub extends JavaPlugin {

    private WorldService worlds;
    private BackStore back;
    private PointStore points;
    private MapBook mapBook;
    private MapMenu mapMenu;
    private GamesMenu gamesMenu;
    private Teleporter teleporter;
    private PlayerWorldListener playerWorldListener;
    private BukkitTask autoUnloadTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "points.yml").exists()) saveResource("points.yml", false);

        worlds = new WorldService(this);
        back = new BackStore(this);
        points = new PointStore(this);
        mapBook = new MapBook(this);
        teleporter = new Teleporter(this);
        mapMenu = new MapMenu(this);
        gamesMenu = new GamesMenu(this);

        back.load();
        worlds.startup();
        points.load();

        playerWorldListener = new PlayerWorldListener(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(playerWorldListener, this);
        pm.registerEvents(new FlagListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new BookListener(this), this);

        bind("world", new WorldCommand(this));
        bind("cghub", new HubCommand(this));
        PluginCommand carte = getCommand("carte");
        if (carte != null) carte.setExecutor((s, c, l, a) -> openFor(s, true));
        PluginCommand jeux = getCommand("jeux");
        if (jeux != null) jeux.setExecutor((s, c, l, a) -> openFor(s, false));

        scheduleAutoUnload();
        getLogger().info("CleanGangHub activé — " + worlds.entries().size() + " monde(s), "
                + points.all().size() + " point(s) sur la carte.");
    }

    @Override
    public void onDisable() {
        if (back != null) back.save();
        if (worlds != null) worlds.save();
    }

    private void bind(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(ex);
        }
    }

    private void scheduleAutoUnload() {
        if (autoUnloadTask != null) autoUnloadTask.cancel();
        autoUnloadTask = null;
        if (!getConfig().getBoolean("auto-unload.enabled", true)) return;
        long every = Math.max(10, getConfig().getLong("auto-unload.check-interval", 60)) * 20L;
        autoUnloadTask = getServer().getScheduler().runTaskTimer(this, worlds::tickAutoUnload, every, every);
    }

    private boolean openFor(CommandSender sender, boolean map) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (map) mapMenu.open(p); else gamesMenu.open(p);
        return true;
    }

    public void reloadAll() {
        reloadConfig();
        worlds.loadFile();
        points.load();
        scheduleAutoUnload();
    }

    public void msg(CommandSender to, String miniMessage) {
        to.sendMessage(Text.mm(getConfig().getString("prefix", "") + miniMessage));
    }

    public WorldService worlds() { return worlds; }
    public BackStore back() { return back; }
    public PointStore points() { return points; }
    public MapBook mapBook() { return mapBook; }
    public MapMenu mapMenu() { return mapMenu; }
    public GamesMenu gamesMenu() { return gamesMenu; }
    public Teleporter teleporter() { return teleporter; }
    public PlayerWorldListener playerWorldListener() { return playerWorldListener; }
}

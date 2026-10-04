package fr.cleangang.hub;

import fr.cleangang.hub.command.HubCommand;
import fr.cleangang.hub.gui.GamesMenu;
import fr.cleangang.hub.gui.MapMenu;
import fr.cleangang.hub.gui.MenuListener;
import fr.cleangang.hub.listener.BookListener;
import fr.cleangang.hub.map.MapBook;
import fr.cleangang.hub.map.PointStore;
import fr.cleangang.hub.util.Teleporter;
import fr.cleangang.hub.util.Text;
import fr.cleangang.hub.world.WorldManager;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class CleanGangHub extends JavaPlugin {

    private WorldManager worlds;
    private PointStore points;
    private MapBook mapBook;
    private MapMenu mapMenu;
    private GamesMenu gamesMenu;
    private Teleporter teleporter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "points.yml").exists()) {
            saveResource("points.yml", false);
        }

        worlds = new WorldManager(this);
        points = new PointStore(this);
        mapBook = new MapBook(this);
        teleporter = new Teleporter(this);
        mapMenu = new MapMenu(this);
        gamesMenu = new GamesMenu(this);

        worlds.loadAll();
        points.load();

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new BookListener(this), this);
        getServer().getPluginManager().registerEvents(worlds, this);

        HubCommand hub = new HubCommand(this);
        PluginCommand cg = getCommand("cghub");
        if (cg != null) {
            cg.setExecutor(hub);
            cg.setTabCompleter(hub);
        }
        PluginCommand carte = getCommand("carte");
        if (carte != null) carte.setExecutor((s, c, l, a) -> openFor(s, true));
        PluginCommand jeux = getCommand("jeux");
        if (jeux != null) jeux.setExecutor((s, c, l, a) -> openFor(s, false));

        getLogger().info("CleanGangHub activé — " + points.all().size() + " point(s) sur la carte.");
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
        points.load();
        worlds.loadAll();
    }

    public void msg(CommandSender to, String miniMessage) {
        to.sendMessage(Text.mm(getConfig().getString("prefix", "") + miniMessage));
    }

    public WorldManager worlds() { return worlds; }
    public PointStore points() { return points; }
    public MapBook mapBook() { return mapBook; }
    public MapMenu mapMenu() { return mapMenu; }
    public GamesMenu gamesMenu() { return gamesMenu; }
    public Teleporter teleporter() { return teleporter; }
}

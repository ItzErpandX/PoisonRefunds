package me.erpand.PoisonRefunds;

import me.erpand.PoisonRefunds.commands.*;
import me.erpand.PoisonRefunds.database.DatabaseManager;
import me.erpand.PoisonRefunds.events.PlayerDeathListener;
import me.erpand.PoisonRefunds.events.PlayerJoinListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.BukkitLibraryManager;

public class PoisonRefunds extends JavaPlugin {
    private DatabaseManager databaseManager;
    private MiniMessage mm;

    @Override
    public void onEnable() {
        try {
            loadExternalLibraries();
        } catch (Exception e) {
            getLogger().severe("Failed to load external libraries: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        reloadConfig();

        mm = MiniMessage.miniMessage();

        databaseManager = new DatabaseManager(this);
        databaseManager.initializeDatabase();

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            int deleted = databaseManager.cleanupOldRefunds();
            if (deleted > 0) {
                getLogger().info("Deleted " + deleted + " old refunds.");
            }
        });

        new RefundCodesCommand(this);
        new RefundCommand(this);
        new RefundPackagesCommand(this);
        new PRReloadCommand(this);
        new ClearRefundCodesCommand(this);
        new ManualRefundCommand(this);

        getLogger().info("PoisonRefunds enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.closeConnection();
        getLogger().info("PoisonRefunds disabled!");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MiniMessage getMiniMessage() {
        return mm;
    }

    private void loadExternalLibraries() {
        LibraryManager libraryManager = new BukkitLibraryManager(this);

        try {
            libraryManager.addMavenCentral();
            libraryManager.loadLibrary(
                Library.builder()
                    .groupId("org{}xerial")
                    .artifactId("sqlite-jdbc")
                    .version("3.49.1.0")
                    .relocate("org{}sqlite", "me{}erpand{}PoisonRefunds{}libs{}sqlite")
                    .build()
                    );

            getLogger().info("Libraries loaded successfully!");

        } catch (Exception e) {
            getLogger().severe("An error occurred while loading libraries: " + e.getMessage());
            throw e;
        }
    }
}
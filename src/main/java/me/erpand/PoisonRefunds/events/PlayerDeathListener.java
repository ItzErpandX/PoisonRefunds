package me.erpand.PoisonRefunds.events;

import me.erpand.PoisonRefunds.PoisonRefunds;
import me.erpand.PoisonRefunds.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerDeathListener implements Listener {
    private final PoisonRefunds plugin;
    private final DatabaseManager databaseManager;

    public PlayerDeathListener(PoisonRefunds plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();

    if (Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY))) {
        return;
    }

    List<ItemStack> items = event.getDrops();
    if (items.isEmpty()) {
        return;
    }

    final String deathReason;
    if (player.getKiller() != null) {
        deathReason = "Killed by " + player.getKiller().getName();
    } else {
        deathReason = event.getDeathMessage();
    }

    generateUniqueCodeAsync().thenAccept(code -> {
        if (code == null) {
            plugin.getLogger().severe("Failed to generate unique code for player: " + player.getName());
            return;
        }

        // Perform the database operation asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                databaseManager.storeRefund(player.getUniqueId(), player.getName(), code, deathReason, items);

                // Notify player on the main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Component message = plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.code-generated")
                                    .replace("{code}", code) // Replace placeholder
                    );
                    player.sendMessage(message);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to store refund: " + e.getMessage());
            }
        });
    });
}


    private CompletableFuture<String> generateUniqueCodeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String code;
            try {
                do {
                    code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    if (!databaseManager.isCodeExists(code)) {
                        return code;
                    }
                } while (true);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to generate unique code: " + e.getMessage());
                return null; // Return `null` on failure
            }
        });
    }
}
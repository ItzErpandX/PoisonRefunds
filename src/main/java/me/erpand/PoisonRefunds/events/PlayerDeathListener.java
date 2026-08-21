package me.erpand.PoisonRefunds.events;

import me.erpand.PoisonRefunds.PoisonRefunds;
import me.erpand.PoisonRefunds.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

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

        if (event.getKeepInventory()) {
            return;
        }

        // i realized that i made a mistake by getting items while in async code
        List<ItemStack> items = event.getDrops().stream()
                .map(ItemStack::clone)
                .toList();

        if (items.isEmpty()) {
            return;
        }

        final String deathReason = getDeathReason(event, player);

        generateUniqueCodeAsync().thenAccept(code -> {
            if (code == null) {
                plugin.getLogger().severe(
                        "Failed to generate unique code for player: " + player.getName()
                );
                return;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    databaseManager.storeRefund(
                            player.getUniqueId(),
                            player.getName(),
                            code,
                            deathReason,
                            items
                    );

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String configuredMessage = plugin.getConfig()
                                .getString("messages.code-generated", "");

                        Component message = plugin.getMiniMessage().deserialize(
                                configuredMessage.replace("{code}", code)
                        );

                        player.sendMessage(message);
                    });

                } catch (Exception exception) {
                    plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to store refund for player: " + player.getName(),
                            exception
                    );
                }
            });
        });
    }

    private String getDeathReason(PlayerDeathEvent event, Player player) {
        Player killer = player.getKiller();

        if (killer != null) {
            return "Killed by " + killer.getName();
        }

        Component deathMessage = event.deathMessage();

        if (deathMessage == null) {
            return "Unknown death";
        }

        return PlainTextComponentSerializer.plainText()
                .serialize(deathMessage);
    }

    private CompletableFuture<String> generateUniqueCodeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                while (true) {
                    String code = UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();

                    if (!databaseManager.isCodeExists(code)) {
                        return code;
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Failed to generate unique refund code",
                        exception
                );
                return null;
            }
        });
    }
}
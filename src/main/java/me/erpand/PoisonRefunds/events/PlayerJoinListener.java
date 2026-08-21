package me.erpand.PoisonRefunds.events;

import me.erpand.PoisonRefunds.PoisonRefunds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.sql.SQLException;

public class PlayerJoinListener implements Listener {
    private final PoisonRefunds plugin;

    public PlayerJoinListener(PoisonRefunds plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (plugin.getDatabaseManager().hasApprovedRefunds(player.getUniqueId())) {
                    // Switching back to the main thread to send the message
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.join-notification", "<green>✓ You have approved refunds! Use /refundpackages"))));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check approved refunds for player: " + e.getMessage());
            }
        });
    }
}
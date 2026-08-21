package me.erpand.PoisonRefunds.commands;

import me.erpand.PoisonRefunds.PoisonRefunds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class RefundCommand implements CommandExecutor {
    private final PoisonRefunds plugin;

    public RefundCommand(PoisonRefunds plugin) {
        this.plugin = plugin;
        plugin.getCommand("refund").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.usage-refund", "<red>Usage: /refund <code>")));
            return true;
        }

        // Permission check for players only
        if (sender instanceof Player && !sender.hasPermission("poisonrefunds.approve")) {
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.no-permission", "<red>No permission.")));
            return true;
        }

        String code = args[0].toUpperCase();

        // Execute database operation asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Approve the refund in the database
                boolean approved = plugin.getDatabaseManager().approveRefund(code);

                if (!approved) {
                    // Handle case where refund could not be approved
                    plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.refund-not-found", "<red>Refund not found or already approved."))));
                    return;
                }

                // Fetch the refund owner UUID from the database
                String refundOwnerUUID = plugin.getDatabaseManager().getRefundOwner(code);

                // Perform Bukkit-related actions back on the main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (refundOwnerUUID != null) {
                        UUID ownerUUID = UUID.fromString(refundOwnerUUID);
                        Player refundOwner = plugin.getServer().getPlayer(ownerUUID);

                        // Notify the refund owner if they are online
                        if (refundOwner != null && refundOwner.isOnline()) {
                            refundOwner.sendMessage(plugin.getMiniMessage().deserialize(
                                    plugin.getConfig().getString("messages.refund-approved-notification", "<green>✓ Your refund <gold>{code}</gold> has been approved!")
                                            .replace("{code}", code)
                            ));
                        }

                        // Sender notification about the approval
                        sender.sendMessage(plugin.getMiniMessage().deserialize(
                                plugin.getConfig().getString("messages.refund-approved", "<green>Refund approved: <gold>{code}</gold>")
                                        .replace("{code}", code)
                        ));
                    } else {
                        // Handle error case where refund owner UUID is not found
                        sender.sendMessage(plugin.getMiniMessage().deserialize(
                                plugin.getConfig().getString("messages.error", "<red>⚠ An error occurred. Contact staff.")));
                    }
                });

            } catch (Exception e) {
                // Handle errors and notify the sender on the main thread
                plugin.getLogger().severe("Error approving refund: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.error", "<red>⚠ An error occurred. Contact staff.")
                )));
            }
        });

        return true;
    }
}
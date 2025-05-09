package me.erpand.PoisonRefunds.commands;

import me.erpand.PoisonRefunds.PoisonRefunds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ClearRefundCodesCommand implements CommandExecutor {
    private final PoisonRefunds plugin;

    public ClearRefundCodesCommand(PoisonRefunds plugin) {
        this.plugin = plugin;
        plugin.getCommand("clearrefundcodes").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("poisonrefunds.clear")) {
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.no-permission", "<red>No permission.")));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int deleted = plugin.getDatabaseManager().cleanupOldRefunds();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.cleared-refunds", "<green>Cleared <gold>" + deleted + "</gold> old refund codes.")));
                plugin.getLogger().info("Manual cleanup removed " + deleted + " packages");
            });
        });
        return true;
    }
}
package me.erpand.PoisonRefunds.commands;

import me.erpand.PoisonRefunds.PoisonRefunds;
import me.erpand.PoisonRefunds.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RefundCodesCommand implements CommandExecutor {
    private final PoisonRefunds plugin;
    private final GUIManager guiManager;

    public RefundCodesCommand(PoisonRefunds plugin) {
        this.plugin = plugin;
        this.guiManager = new GUIManager(plugin);
        plugin.getCommand("refundcodes").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players.")));
            return true;
        }

        if (args.length > 0) {
            if (!player.hasPermission("poisonrefunds.view.others")) {
                player.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.no-permission", "<red>You don't have permission to view others' refund codes.")));
                return true;
            }

            OfflinePlayer target = Bukkit.getPlayerExact(args[0]); // Check for an online player first
            if (target == null) {
                target = Bukkit.getOfflinePlayerIfCached(args[0]); // Then check for a cached offline player
            }

            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.player-not-found", "<red>Player not found.")));
                return true;
            }

            guiManager.showRefundCodesGUI(player, target.getUniqueId(), target.getName());
        } else {
            guiManager.showRefundCodesGUI(player, player.getUniqueId(), player.getName());
        }
        return true;
    }
}
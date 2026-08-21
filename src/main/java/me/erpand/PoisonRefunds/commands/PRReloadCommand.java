package me.erpand.PoisonRefunds.commands;

import me.erpand.PoisonRefunds.PoisonRefunds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class PRReloadCommand implements CommandExecutor {
    private final PoisonRefunds plugin;

    public PRReloadCommand(PoisonRefunds plugin) {
        this.plugin = plugin;
        plugin.getCommand("prreload").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.reloadConfig();
            plugin.getConfig();
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.config-reloaded", "<green>Config reloaded.")));
            return true;
        }

        if (!player.hasPermission("poisonrefunds.reload")) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.no-permission", "<red>No permission.")));
            return true;
        }

        plugin.reloadConfig();
        plugin.getConfig();
        player.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getConfig().getString("messages.config-reloaded", "<green>Config reloaded.")));
        return true;
    }
}
package me.erpand.PoisonRefunds.commands;

import me.erpand.PoisonRefunds.PoisonRefunds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public abstract class BaseCommand implements CommandExecutor {
    protected final PoisonRefunds plugin;

    public BaseCommand(PoisonRefunds plugin, String commandName) {
        this.plugin = plugin;
        plugin.getCommand(commandName).setExecutor(this); // Auto-register command
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players.")));
            return true;
        }
        return execute(player, args);
    }

    public abstract boolean execute(@NotNull Player player, String[] args);
}
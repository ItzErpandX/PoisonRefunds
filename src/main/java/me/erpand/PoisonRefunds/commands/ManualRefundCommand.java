package me.erpand.PoisonRefunds.commands;

import dev.triumphteam.gui.guis.Gui;
import me.erpand.PoisonRefunds.PoisonRefunds;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class ManualRefundCommand implements CommandExecutor {
    private final PoisonRefunds plugin;

    public ManualRefundCommand(PoisonRefunds plugin) {
        this.plugin = plugin;
        plugin.getCommand("manualrefund").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.player-only", "<red>✖ Only players can use this command.")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.usage-manualrefund", "<red>Usage: /manualrefund <player>")));
            return true;
        }

        if (!player.hasPermission("poisonrefunds.manual")) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getConfig().getString("messages.no-permission", "<red>✖ You don't have permission!")));
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


        // Open the manual refund GUI
        openManualRefundGUI(player, target);
        return true;
    }

    private void openManualRefundGUI(Player admin, OfflinePlayer target) {
        Gui gui = Gui.gui()
            .title(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("gui.manual-refund.title", "<green>Refunding: <white>{player}</white>")
                                .replace("{player}", target.getName())))
            .rows(6)
            .create();

        // Register close handler
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onInventoryClose(InventoryCloseEvent event) {
                if (event.getInventory().equals(gui.getInventory())) {
                    List<ItemStack> items = new ArrayList<>();
                    for (ItemStack item : event.getInventory().getContents()) {
                        if (item != null && !item.getType().isAir()) {
                            items.add(item.clone());
                        }
                    }

                    if (!items.isEmpty()) {
                        createManualRefundPackage(admin, target, items);
                    }

                    // Unregister this listener after use
                    HandlerList.unregisterAll(this);
                }
            }
        }, plugin);

        gui.open(admin);
    }

    private void createManualRefundPackage(Player admin, OfflinePlayer target, List<ItemStack> items) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Generate a manual refund code
                String code = "MANUAL-" + System.currentTimeMillis();
                String currentDate = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                // Store in the database
                plugin.getDatabaseManager().storeManualRefund(
                        target.getUniqueId(),
                        target.getName(),
                        code,
                        "Manual refund by " + admin.getName(),
                        currentDate,
                        items
                );

                // Notify admin
                plugin.getServer().getScheduler().runTask(plugin, () -> admin.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.manual-refund-created",
                                        "<green>Created manual refund package for <white>{player}</white>")
                                .replace("{player}", target.getName()))));

                // Notify player if online
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> onlineTarget.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.refund-received",
                                            "<green>You have received a refund package from <white>{admin}</white>")
                                    .replace("{admin}", admin.getName()))));
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create manual refund: " + e.getMessage());
            }
        });
    }
}
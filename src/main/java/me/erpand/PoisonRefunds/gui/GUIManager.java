package me.erpand.PoisonRefunds.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.erpand.PoisonRefunds.PoisonRefunds;
import me.erpand.PoisonRefunds.models.RefundPackage;
import me.erpand.PoisonRefunds.models.RefundRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class GUIManager {
    private final PoisonRefunds plugin;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private final MiniMessage mm;

    public GUIManager(PoisonRefunds plugin) {
        this.plugin = plugin;
        this.mm = plugin.getMiniMessage();
    }

    // Refund Codes GUI
    public void showRefundCodesGUI(Player viewer, UUID targetUuid, String targetName) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<RefundRecord> records = plugin.getDatabaseManager().getRefundRecords(targetUuid);

            if (records.isEmpty()) {
                viewer.sendMessage(mm.deserialize(
                        plugin.getConfig().getString("messages.no-refund-codes", "<yellow>⚠ No refund codes.")));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Gui gui = Gui.gui()
                        .title(plugin.getMiniMessage().deserialize(
                                plugin.getConfig().getString("gui.refund-codes.title", "<gold>Refund Codes</gold>")
                                        .replace("{player}", targetName)
                        ))
                        .rows(6)
                        .create();

                gui.setDefaultClickAction(event -> event.setCancelled(true));

                records.forEach(record -> {
                    GuiItem item = new GuiItem(createRefundCodeItem(record));
                    item.setAction(event -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            List<ItemStack> items = plugin.getDatabaseManager().getRefundItems(record.code());
                            plugin.getServer().getScheduler().runTask(plugin, () -> showPreviewGUI(viewer, targetUuid, targetName, record.code(), items));
                        } catch (SQLException e) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> viewer.sendMessage(plugin.getMiniMessage().deserialize(
                                    plugin.getConfig().getString("messages.error", "<red>⚠ An error occurred. Contact staff."))));
                        }
                    }));
                    gui.addItem(item);
                });

                gui.open(viewer);
            });
        });
    }

    private ItemStack createRefundCodeItem(RefundRecord record) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        String statusDisplay = record.status().equals("APPROVED")
                ? "<green>APPROVED</green>"
                : "<yellow>PENDING</yellow>";

        // Fixed display name with italic removal
        meta.displayName(mm.deserialize(
                plugin.getConfig().getString("gui.refund-codes.items.code-item.name")
                        .replace("{code}", record.code())
                        .replace("{status}", statusDisplay)
        ).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));

        // Fixed lore with italic removal
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig()
                .getStringList("gui.refund-codes.items.code-item.lore")) {
            lore.add(mm.deserialize(line
                            .replace("{date}", formatDate(record.date()))
                            .replace("{reason}", record.reason()))
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        meta.lore(lore);

        // Store the code for click handling
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "refund_code"),
                PersistentDataType.STRING, record.code());

        item.setItemMeta(meta);
        return item;
    }

    private String formatDate(String isoDate) {
        try {
            LocalDateTime date = LocalDateTime.parse(isoDate);
            return date.format(dateFormatter);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("Failed to parse date: " + isoDate);
            return isoDate;
        }
    }

    // Refund Packages GUI
    public void showRefundPackagesGUI(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<RefundPackage> packages = plugin.getDatabaseManager().getApprovedRefundPackages(player.getUniqueId());

            if (packages.isEmpty()) {
                player.sendMessage(mm.deserialize(
                        plugin.getConfig().getString("messages.no-refund-packages", "<yellow>⚠ No approved refund packages available.")));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Gui gui = Gui.gui()
                        .title(plugin.getMiniMessage().deserialize(
                                plugin.getConfig().getString("gui.refund-packages.title", "<gold>Your Refund Packages</gold>")
                        ))
                        .rows(6)
                        .create();

                gui.setDefaultClickAction(event -> event.setCancelled(true));

                packages.forEach(pkg -> {
                    GuiItem item = new GuiItem(createPackageItem(pkg));
                    item.setAction(event -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            if (plugin.getDatabaseManager().claimRefundPackage(player.getUniqueId(), pkg.code())) {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    pkg.items().forEach(itemStack -> {
                                        if (itemStack != null) {
                                            player.getInventory().addItem(itemStack).values()
                                                    .forEach(leftover -> player.getWorld()
                                                            .dropItemNaturally(player.getLocation(), leftover));
                                        }
                                    });
                                    player.sendMessage(plugin.getMiniMessage().deserialize(
                                            plugin.getConfig().getString("messages.package-claimed")
                                                    .replace("{code}", pkg.code())
                                                    .replace("{item_count}", String.valueOf(pkg.items().size()))
                                    ));
                                    gui.close(player);
                                });
                            }
                        } catch (SQLException e) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(plugin.getMiniMessage().deserialize(
                                    plugin.getConfig().getString("messages.error", "<red>⚠ An error occurred. Contact staff."))));
                        }
                    }));
                    gui.addItem(item);
                });

                gui.open(player);
            });
        });
    }

    private ItemStack createPackageItem(RefundPackage pkg) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        // Fixed display name with italic removal
        meta.displayName(mm.deserialize(
                plugin.getConfig().getString("gui.refund-packages.items.package-item.name")
                        .replace("{code}", pkg.code())
                        .replace("{item_count}", String.valueOf(pkg.items().size()))
        ).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));

        // Fixed lore with italic removal
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig()
                .getStringList("gui.refund-packages.items.package-item.lore")) {
            lore.add(mm.deserialize(line
                            .replace("{date}", formatDate(pkg.date()))
                            .replace("{reason}", pkg.reason()))
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        meta.lore(lore);

        // Store the code for click handling
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "refund_code"),
                PersistentDataType.STRING, pkg.code());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Displays a preview graphical user interface (GUI) for a refund code with relevant items and actions.
     *
     * @param viewer the player who is viewing the GUI
     * @param targetUuid the UUID of the player associated with the refund
     * @param targetName the name of the player associated with the refund
     * @param code the unique refund code being previewed
     * @param items the list of {@code ItemStack} objects representing items in the refund
     */
    public void showPreviewGUI(Player viewer, UUID targetUuid, String targetName, String code, List<ItemStack> items) {
        Gui gui = Gui.gui()
                .title(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("gui.preview.title", "<gold>Preview » {code}</gold>")
                                .replace("{code}", code)
                ))
                .rows(6)
                .disableAllInteractions()
                .create();

        // Add all refund items
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                gui.addItem(new GuiItem(item));
            }
        }

        // Create Approve Button
        ItemStack approveButton = new ItemStack(Material.EMERALD);
        ItemMeta approveMeta = approveButton.getItemMeta();
        approveMeta.displayName(plugin.getMiniMessage()
                .deserialize("<green>APPROVE REFUND</green>")
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        approveButton.setItemMeta(approveMeta);

        gui.setItem(53, new GuiItem(approveButton, event -> {
            Player clicker = (Player) event.getWhoClicked();

            // Permission check
            if (!clicker.hasPermission("poisonrefunds.approve")) {
                clicker.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.no-permission", "<red>✖ You don't have permission!")));
                return;
            }

            // Execute approval in an asynchronous task
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String refundOwnerUUID = plugin.getDatabaseManager().getRefundOwner(code);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (refundOwnerUUID != null) {
                            UUID ownerUUID = UUID.fromString(refundOwnerUUID);
                            Player owner = plugin.getServer().getPlayer(ownerUUID);

                            // Run the refund command
                            plugin.getCommand("refund").execute(clicker, "refund", new String[]{code});

                            // Notify the refund owner if online
                            if (owner != null && owner.isOnline()) {
                                owner.sendMessage(plugin.getMiniMessage().deserialize(
                                        plugin.getConfig().getString("messages.refund-approved-notification")
                                                .replace("{code}", code)
                                ));
                            }
                        } else {
                            clicker.sendMessage(plugin.getMiniMessage().deserialize(
                                    plugin.getConfig().getString("messages.error", "<red>⚠ An error occurred. Contact staff.")));
                        }

                        // Close the GUI
                        gui.close(clicker);
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to approve refund: " + e.getMessage());

                    // Notify the player about the error on the main thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> clicker.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.error", "<red>⚠ An error occurred. Contact staff.")
                    )));
                }
            });
        }));

        // Create Back Button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(plugin.getMiniMessage()
                .deserialize("<gray>« Back</gray>")
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        backButton.setItemMeta(backMeta);

        gui.setItem(49, new GuiItem(backButton, event -> {
            // Redirect to the refund codes GUI of the original target
            showRefundCodesGUI(viewer, targetUuid, targetName);
        }));

        // Open the GUI for the viewer
        gui.open(viewer);
    }
}
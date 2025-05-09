package me.erpand.PoisonRefunds.commands;

import me.erpand.PoisonRefunds.PoisonRefunds;
import me.erpand.PoisonRefunds.gui.GUIManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RefundPackagesCommand extends BaseCommand {
    public RefundPackagesCommand(PoisonRefunds plugin) {
        super(plugin, "refundpackages");
    }

    @Override
    public boolean execute(@NotNull Player player, String[] args) {
        new GUIManager(plugin).showRefundPackagesGUI(player);
        return true;
    }
}
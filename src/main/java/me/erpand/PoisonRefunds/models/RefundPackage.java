package me.erpand.PoisonRefunds.models;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public record RefundPackage(String code, String reason, String date, List<ItemStack> items) {}
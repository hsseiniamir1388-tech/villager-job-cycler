package dev.villagercycler;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;
import java.util.List;

/** Reads enchanted-book offers from a villager's trade list. */
public final class TradeScanner {
    public record Match(String id, int level, int price) {}

    private record Book(String id, int level, int price) {}

    private TradeScanner() {}

    private static List<Book> books(TradeOfferList offers) {
        List<Book> out = new ArrayList<>();
        for (TradeOffer offer : offers) {
            ItemStack sell = offer.getSellItem();
            if (!sell.isOf(Items.ENCHANTED_BOOK)) continue;
            ItemEnchantmentsComponent comp = sell.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (comp == null) continue;
            int price = offer.getDisplayedFirstBuyItem().getCount();
            for (RegistryEntry<Enchantment> entry : comp.getEnchantments()) {
                String id = entry.getKey().map(k -> k.getValue().getPath()).orElse("?");
                out.add(new Book(id, comp.getLevel(entry), price));
            }
        }
        return out;
    }

    /** Returns the cheapest book that satisfies any target, or null. */
    public static Match find(TradeOfferList offers, List<CyclerConfig.Target> targets) {
        Match best = null;
        for (Book b : books(offers)) {
            for (CyclerConfig.Target t : targets) {
                if (t.enchant.equals(b.id()) && b.level() >= t.minLevel && b.price() <= t.maxPrice) {
                    if (best == null || b.price() < best.price()) {
                        best = new Match(b.id(), b.level(), b.price());
                    }
                }
            }
        }
        return best;
    }

    public static String describe(TradeOfferList offers) {
        List<Book> list = books(offers);
        if (list.isEmpty()) return "no book";
        StringBuilder sb = new StringBuilder();
        for (Book b : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(EnchantInfo.pretty(b.id())).append(' ').append(b.level()).append(" @").append(b.price());
        }
        return sb.toString();
    }
}

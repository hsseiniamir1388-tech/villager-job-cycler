package dev.villagercycler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Vanilla enchantments that can appear in librarian books, with their max levels. */
public final class EnchantInfo {
    private static final Map<String, Integer> MAX = new LinkedHashMap<>();
    public static final List<String> IDS;

    static {
        MAX.put("mending", 1);
        MAX.put("unbreaking", 3);
        MAX.put("efficiency", 5);
        MAX.put("fortune", 3);
        MAX.put("silk_touch", 1);
        MAX.put("looting", 3);
        MAX.put("sharpness", 5);
        MAX.put("smite", 5);
        MAX.put("bane_of_arthropods", 5);
        MAX.put("knockback", 2);
        MAX.put("fire_aspect", 2);
        MAX.put("sweeping_edge", 3);
        MAX.put("protection", 4);
        MAX.put("fire_protection", 4);
        MAX.put("feather_falling", 4);
        MAX.put("blast_protection", 4);
        MAX.put("projectile_protection", 4);
        MAX.put("respiration", 3);
        MAX.put("aqua_affinity", 1);
        MAX.put("thorns", 3);
        MAX.put("depth_strider", 3);
        MAX.put("frost_walker", 2);
        MAX.put("power", 5);
        MAX.put("punch", 2);
        MAX.put("flame", 1);
        MAX.put("infinity", 1);
        MAX.put("multishot", 1);
        MAX.put("piercing", 4);
        MAX.put("quick_charge", 3);
        MAX.put("loyalty", 3);
        MAX.put("riptide", 3);
        MAX.put("channeling", 1);
        MAX.put("impaling", 5);
        MAX.put("luck_of_the_sea", 3);
        MAX.put("lure", 3);
        MAX.put("curse_of_vanishing", 1);
        MAX.put("curse_of_binding", 1);
        IDS = List.copyOf(MAX.keySet());
    }

    private EnchantInfo() {}

    public static int maxLevel(String id) {
        return MAX.getOrDefault(id, 5);
    }

    public static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}

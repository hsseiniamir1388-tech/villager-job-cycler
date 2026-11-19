package dev.villagercycler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CyclerConfig {
    public static final class Target {
        public String enchant = "mending";
        public int minLevel = 1;
        public int maxPrice = 30;

        public Target() {}

        public Target(String enchant, int minLevel, int maxPrice) {
            this.enchant = enchant;
            this.minLevel = minLevel;
            this.maxPrice = maxPrice;
        }

        public String label() {
            return EnchantInfo.pretty(enchant) + " lvl>=" + minLevel + " price<=" + maxPrice;
        }
    }

    /** Delay (in ticks) between actions. SAFE is the default for online servers. */
    public enum Speed {
        SAFE(12, 24), NORMAL(7, 14), FAST(4, 8);

        public final int min;
        public final int max;

        Speed(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public Speed next() {
            Speed[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public List<Target> targets = new ArrayList<>();
    public Speed speed = Speed.SAFE;
    public int maxAttempts = 300;
    public boolean playSound = true;
    public boolean stopOnDamage = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CyclerConfig instance;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("villagercycler.json");
    }

    public static CyclerConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        boolean fresh = true;
        try {
            Path p = path();
            if (Files.exists(p)) {
                fresh = false;
                instance = GSON.fromJson(Files.readString(p), CyclerConfig.class);
            }
        } catch (Exception e) {
            instance = null;
        }
        if (instance == null) {
            instance = new CyclerConfig();
            fresh = true;
        }
        if (instance.targets == null) instance.targets = new ArrayList<>();
        if (instance.speed == null) instance.speed = Speed.SAFE;
        if (fresh) {
            instance.targets.add(new Target("mending", 1, 30));
            save();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}

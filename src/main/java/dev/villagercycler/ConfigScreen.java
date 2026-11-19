package dev.villagercycler;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Settings menu (opened with J). English only: Minecraft's default font cannot shape Persian text. */
public class ConfigScreen extends Screen {
    private static final int MAX_TARGETS = 8;

    private static int editIdx = 0;
    private static int editLevel = 1;
    private static int editPrice = 30;

    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Villager Job Cycler"));
        this.parent = parent;
    }

    private ButtonWidget add(String label, int x, int y, int w, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, w, 20).build());
    }

    private void label(String text, int x, int y, int w) {
        ButtonWidget b = add(text, x, y, w, btn -> {});
        b.active = false;
    }

    private void refresh() {
        CyclerConfig.save();
        this.clearAndInit();
    }

    private static int maxLevelOfSelected() {
        return EnchantInfo.maxLevel(EnchantInfo.IDS.get(editIdx));
    }

    @Override
    protected void init() {
        CyclerConfig cfg = CyclerConfig.get();
        int n = EnchantInfo.IDS.size();
        String id = EnchantInfo.IDS.get(editIdx);
        int max = EnchantInfo.maxLevel(id);
        if (editLevel > max) editLevel = max;
        if (editLevel < 1) editLevel = 1;

        int lx = this.width / 2 - 205;
        int rx = this.width / 2 + 5;
        int y = 36;

        // --- enchantment selector
        add("<", lx, y, 20, b -> {
            editIdx = (editIdx + n - 1) % n;
            refresh();
        });
        label(EnchantInfo.pretty(id), lx + 22, y, 156);
        add(">", lx + 180, y, 20, b -> {
            editIdx = (editIdx + 1) % n;
            refresh();
        });

        // --- min level
        y += 24;
        add("-", lx, y, 20, b -> {
            editLevel = Math.max(1, editLevel - 1);
            refresh();
        });
        label("Min level: " + editLevel + " / " + max, lx + 22, y, 156);
        add("+", lx + 180, y, 20, b -> {
            editLevel = Math.min(maxLevelOfSelected(), editLevel + 1);
            refresh();
        });

        // --- max price
        y += 24;
        add("-5", lx, y, 24, b -> {
            editPrice = Math.max(1, editPrice - 5);
            refresh();
        });
        add("-1", lx + 26, y, 24, b -> {
            editPrice = Math.max(1, editPrice - 1);
            refresh();
        });
        label("Max price: " + editPrice + " em", lx + 52, y, 96, 20);
        add("+1", lx + 150, y, 24, b -> {
            editPrice = Math.min(64, editPrice + 1);
            refresh();
        });
        add("+5", lx + 176, y, 24, b -> {
            editPrice = Math.min(64, editPrice + 5);
            refresh();
        });

        // --- add target
        y += 28;
        ButtonWidget addBtn = add("Add target", lx, y, 200, b -> {
            if (cfg.targets.size() < MAX_TARGETS) {
                cfg.targets.add(new CyclerConfig.Target(EnchantInfo.IDS.get(editIdx), editLevel, editPrice));
                refresh();
            }
        });
        addBtn.active = cfg.targets.size() < MAX_TARGETS;

        // --- global settings
        y += 34;
        add("Speed: " + cfg.speed, lx, y, 200, b -> {
            cfg.speed = cfg.speed.next();
            refresh();
        });
        y += 24;
        add("-50", lx, y, 30, b -> {
            cfg.maxAttempts = Math.max(10, cfg.maxAttempts - 50);
            refresh();
        });
        label("Max tries: " + cfg.maxAttempts, lx + 32, y, 136);
        add("+50", lx + 170, y, 30, b -> {
            cfg.maxAttempts = Math.min(5000, cfg.maxAttempts + 50);
            refresh();
        });
        y += 24;
        add("Stop when hurt: " + (cfg.stopOnDamage ? "ON" : "OFF"), lx, y, 200, b -> {
            cfg.stopOnDamage = !cfg.stopOnDamage;
            refresh();
        });
        y += 24;
        add("Sound on find: " + (cfg.playSound ? "ON" : "OFF"), lx, y, 200, b -> {
            cfg.playSound = !cfg.playSound;
            refresh();
        });

        // --- targets list (right column)
        List<CyclerConfig.Target> list = cfg.targets;
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            int ry = 48 + i * 22;
            label(list.get(i).label(), rx, ry, 176);
            add("X", rx + 178, ry, 22, b -> {
                cfg.targets.remove(idx);
                refresh();
            });
        }

        add("Done", this.width / 2 - 100, this.height - 28, 200, b -> close());
    }

    private void label(String text, int x, int y, int w, int h) {
        label(text, x, y, w);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);
        int rx = this.width / 2 + 5;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Targets (any match stops the cycle):"), rx, 36, 0xFFFFFF);
        int hintY = this.height - 56;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("K: select villager / lectern spot, start, stop   |   ESC: emergency stop"),
                this.width / 2, hintY, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Main hand: axe   |   Offhand: lectern (stack of 2+ recommended)"),
                this.width / 2, hintY + 11, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        CyclerConfig.save();
        if (this.client != null) this.client.setScreen(parent);
    }
}

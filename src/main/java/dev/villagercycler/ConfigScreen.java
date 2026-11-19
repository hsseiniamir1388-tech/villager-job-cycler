package dev.villagercycler;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** Settings menu (opened with J). English only: Minecraft's default font cannot shape Persian text. */
public class ConfigScreen extends Screen {
    private static final int MAX_TARGETS = 6;
    private static final int PER_PAGE = 6;

    private static int tab = 0;
    private static String searchText = "";
    private static int page = 0;

    private final Screen parent;
    private TextFieldWidget searchField;
    private final ButtonWidget[] resultBtns = new ButtonWidget[PER_PAGE];
    private final String[] resultIds = new String[PER_PAGE];
    private ButtonWidget prevBtn;
    private ButtonWidget nextBtn;
    private ButtonWidget pageLabel;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Villager Job Cycler"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- helpers

    private ButtonWidget add(String label, int x, int y, int w, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, w, 20).build());
    }

    private ButtonWidget label(String text, int x, int y, int w) {
        ButtonWidget b = add(text, x, y, w, btn -> {});
        b.active = false;
        return b;
    }

    private void refresh() {
        CyclerConfig.save();
        this.clearAndInit();
    }

    private static void clampPrice(CyclerConfig.Target t) {
        t.maxPrice = MathHelper.clamp(t.maxPrice, EnchantInfo.minPrice(t.enchant, t.minLevel), 64);
    }

    private static boolean hasTarget(String id) {
        for (CyclerConfig.Target t : CyclerConfig.get().targets) {
            if (t.enchant.equals(id)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        int left = this.width / 2 - 200;

        ButtonWidget t0 = add("Targets", left, 28, 100, b -> {
            tab = 0;
            refresh();
        });
        t0.active = tab != 0;
        ButtonWidget t1 = add("Settings", left + 102, 28, 100, b -> {
            tab = 1;
            refresh();
        });
        t1.active = tab != 1;

        if (tab == 0) initTargets(left);
        else initSettings();

        add("Done", left + 250, this.height - 28, 150, b -> close());
    }

    private void initTargets(int left) {
        CyclerConfig cfg = CyclerConfig.get();

        searchField = new TextFieldWidget(this.textRenderer, left, 56, 400, 20, Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search enchantment (e.g. sharp, fort, eff, loot)..."));
        searchField.setMaxLength(32);
        searchField.setText(searchText);
        searchField.setChangedListener(t -> {
            searchText = t;
            page = 0;
            updateResults();
        });
        addDrawableChild(searchField);

        for (int i = 0; i < PER_PAGE; i++) {
            final int idx = i;
            int col = i % 2;
            int row = i / 2;
            resultBtns[i] = add("", left + col * 202, 80 + row * 22, 198, b -> onPick(idx));
        }
        prevBtn = add("< Prev", left, 148, 70, b -> {
            page = Math.max(0, page - 1);
            updateResults();
        });
        pageLabel = label("", left + 72, 148, 256);
        nextBtn = add("Next >", left + 330, 148, 70, b -> {
            page++;
            updateResults();
        });
        updateResults();

        // --- the current target list
        List<CyclerConfig.Target> list = cfg.targets;
        for (int i = 0; i < list.size(); i++) {
            final CyclerConfig.Target t = list.get(i);
            final int idx = i;
            int y = 196 + i * 22;
            label(EnchantInfo.pretty(t.enchant), left, y, 160);
            add("-", left + 162, y, 20, b -> {
                t.minLevel = Math.max(1, t.minLevel - 1);
                clampPrice(t);
                refresh();
            });
            label("Lv " + EnchantInfo.roman(t.minLevel) + "+", left + 184, y, 44);
            add("+", left + 230, y, 20, b -> {
                t.minLevel = Math.min(EnchantInfo.maxLevel(t.enchant), t.minLevel + 1);
                clampPrice(t);
                refresh();
            });
            add("-", left + 256, y, 20, b -> {
                t.maxPrice -= hasShiftDown() ? 5 : 1;
                clampPrice(t);
                refresh();
            });
            label("<= " + t.maxPrice, left + 278, y, 50, 20);
            add("+", left + 330, y, 20, b -> {
                t.maxPrice += hasShiftDown() ? 5 : 1;
                clampPrice(t);
                refresh();
            });
            add("X", left + 354, y, 26, b -> {
                cfg.targets.remove(idx);
                refresh();
            });
        }

        ButtonWidget reset = add("Reset defaults", left, this.height - 28, 150, b -> {
            cfg.targets = CyclerConfig.defaultTargets();
            refresh();
        });
        reset.active = true;
    }

    private void label(String text, int x, int y, int w, int h) {
        label(text, x, y, w);
    }

    private void updateResults() {
        List<String> filtered = EnchantInfo.search(searchText);
        int pages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
        page = MathHelper.clamp(page, 0, pages - 1);
        boolean full = CyclerConfig.get().targets.size() >= MAX_TARGETS;
        for (int i = 0; i < PER_PAGE; i++) {
            int k = page * PER_PAGE + i;
            ButtonWidget b = resultBtns[i];
            if (k < filtered.size()) {
                String id = filtered.get(k);
                resultIds[i] = id;
                boolean added = hasTarget(id);
                b.setMessage(Text.literal(EnchantInfo.pretty(id) + (added ? "  [added]" : "  (max " + EnchantInfo.roman(EnchantInfo.maxLevel(id)) + ")")));
                b.visible = true;
                b.active = !added && !full;
            } else {
                resultIds[i] = null;
                b.visible = false;
            }
        }
        pageLabel.setMessage(Text.literal("Page " + (page + 1) + "/" + pages + "   -   " + filtered.size() + " enchantments"));
        prevBtn.active = page > 0;
        nextBtn.active = page < pages - 1;
    }

    private void onPick(int idx) {
        String id = resultIds[idx];
        CyclerConfig cfg = CyclerConfig.get();
        if (id == null || hasTarget(id) || cfg.targets.size() >= MAX_TARGETS) return;
        int lvl = EnchantInfo.maxLevel(id);
        CyclerConfig.Target t = new CyclerConfig.Target(id, lvl, 20);
        clampPrice(t);
        cfg.targets.add(t);
        refresh();
    }

    private void initSettings() {
        CyclerConfig cfg = CyclerConfig.get();
        int x = this.width / 2 - 100;
        int y = 70;
        add("Speed: " + cfg.speed, x, y, 200, b -> {
            cfg.speed = cfg.speed.next();
            refresh();
        });
        y += 26;
        add("-50", x, y, 30, b -> {
            cfg.maxAttempts = Math.max(10, cfg.maxAttempts - 50);
            refresh();
        });
        label("Max tries: " + cfg.maxAttempts, x + 32, y, 136);
        add("+50", x + 170, y, 30, b -> {
            cfg.maxAttempts = Math.min(5000, cfg.maxAttempts + 50);
            refresh();
        });
        y += 26;
        add("Stop when hurt: " + (cfg.stopOnDamage ? "ON" : "OFF"), x, y, 200, b -> {
            cfg.stopOnDamage = !cfg.stopOnDamage;
            refresh();
        });
        y += 26;
        add("Sound on find: " + (cfg.playSound ? "ON" : "OFF"), x, y, 200, b -> {
            cfg.playSound = !cfg.playSound;
            refresh();
        });
        y += 26;
        add("Debug chat: " + (cfg.debug ? "ON" : "OFF"), x, y, 200, b -> {
            cfg.debug = !cfg.debug;
            refresh();
        });
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = this.width / 2 - 200;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        if (tab == 0) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Your targets - any match stops (Shift = +/-5 on price, X removes):"), left, 182, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Book price = 2 + rand(0..4+10*lvl) + 3*lvl (x2 Mending/Frost Walker/curses), max 64."),
                    left, this.height - 54, 0xAAAAAA);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("e.g. Protection IV costs 14-58, Mending 10-38. 'Lv IV+' = that level or higher."),
                    left, this.height - 43, 0xAAAAAA);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("K: select villager / lectern spot, start, stop   |   ESC: emergency stop"),
                    this.width / 2, 210, 0xAAAAAA);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Main hand: axe   |   Offhand: lectern (stack of 2+ recommended)"),
                    this.width / 2, 222, 0xAAAAAA);
        }
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

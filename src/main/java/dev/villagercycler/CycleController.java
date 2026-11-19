package dev.villagercycler;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.Random;

/**
 * State machine that re-rolls a librarian's trades.
 *
 * Cycle: open trades -> evaluate -> (no match) close -> break lectern with axe -> pick it up ->
 * (restore to offhand) -> wait until villager loses its job -> place lectern at the same spot ->
 * wait until villager takes the job -> open trades again.
 *
 * Everything goes through the normal client interaction manager (same packets as a real player),
 * with smooth camera rotation, vanilla reach limits and randomized delays.
 */
public final class CycleController {
    private enum State {
        IDLE, OPEN_TRADES, WAIT_OFFERS, CLOSE_SCREEN, BREAK, CONFIRM_BREAK, WAIT_PICKUP, RESTORE, WAIT_LOSE, PLACE, CONFIRM_PLACE, WAIT_JOB
    }

    private static final Random RNG = new Random();

    private State state = State.IDLE;
    private int villagerId = -1;
    private BlockPos lecternPos;
    private BlockPos supportPos;
    private Direction face;

    private int wait;
    private int stateTicks;
    private int age;
    private int attempts;
    private int openRetries;
    private int restoreTries;
    private int lecternsBefore;
    private boolean readingDone;
    private boolean breakingStarted;
    private int onTargetTicks;
    private int needSteady;
    private int confirmFails;
    private double jx, jy, jz;
    private String lastSeen = "-";

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public void reset() {
        state = State.IDLE;
    }

    // ------------------------------------------------------------------ selection / start / stop

    public void onToggleKey(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null) return;
        if (isRunning()) {
            stop(client, "Stopped.");
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof VillagerEntity v) {
            villagerId = v.getId();
            say(client, "Villager selected.");
            detectSpot(w, v);
        } else if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos clicked = bhr.getBlockPos();
            if (w.getBlockState(clicked).isOf(Blocks.LECTERN)) {
                setSpotFromLectern(w, clicked);
                say(client, "Lectern spot selected.");
            } else {
                lecternPos = clicked.offset(bhr.getSide());
                supportPos = clicked;
                face = bhr.getSide();
                say(client, "Lectern spot selected (will be placed against the block you aimed at).");
            }
        }
        start(client);
    }

    private void detectSpot(ClientWorld w, VillagerEntity v) {
        BlockPos base = v.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (w.getBlockState(pos).isOf(Blocks.LECTERN)) {
                        double d = pos.getSquaredDistance(base);
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        if (best != null) setSpotFromLectern(w, best);
    }

    private void setSpotFromLectern(ClientWorld w, BlockPos pos) {
        lecternPos = pos;
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction d : order) {
            BlockPos s = pos.offset(d);
            Direction clickFace = d.getOpposite();
            if (w.getBlockState(s).isSideSolidFullSquare(w, s, clickFace)) {
                supportPos = s;
                face = clickFace;
                return;
            }
        }
        supportPos = null;
        face = null;
    }

    private void start(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        CyclerConfig cfg = CyclerConfig.get();

        if (villagerId < 0) {
            say(client, "Aim at a villager and press the key to select it.");
            return;
        }
        if (lecternPos == null) {
            say(client, "No lectern spot yet. Aim at the lectern (or the block it goes against) and press the key.");
            return;
        }
        if (supportPos == null || face == null) {
            say(client, "The lectern has no solid block to be placed against. Select the support block instead.");
            return;
        }
        if (cfg.targets.isEmpty()) {
            say(client, "Add at least one target in the menu (J).");
            return;
        }
        if (client.interactionManager == null || !client.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            say(client, "Survival (or adventure) mode is required.");
            return;
        }
        Entity e = w.getEntityById(villagerId);
        if (!(e instanceof VillagerEntity v)) {
            say(client, "Villager not found. Select it again.");
            villagerId = -1;
            return;
        }
        if (v.isBaby()) {
            say(client, "Baby villagers cannot take jobs.");
            return;
        }
        if (!p.getMainHandStack().isIn(ItemTags.AXES)) {
            say(client, "Hold an axe in your main hand.");
            return;
        }
        if (!p.getOffHandStack().isOf(Items.LECTERN)) {
            say(client, "Hold lecterns in your offhand (a stack of 2+ is best).");
            return;
        }
        if (p.distanceTo(v) > 5.0f) {
            say(client, "Stand closer to the villager.");
            return;
        }

        say(client, "Started. Reminder: make sure automation like this is allowed on your server. ESC = emergency stop.");
        attempts = 0;
        openRetries = 0;
        restoreTries = 0;
        confirmFails = 0;
        lastSeen = "-";
        wait = 0;

        String prof = profession(v);
        boolean lecternHere = w.getBlockState(lecternPos).isOf(Blocks.LECTERN);
        if (prof.equals("nitwit")) {
            say(client, "This villager is a nitwit - it can never get a job.");
            state = State.IDLE;
        } else if (prof.equals("none")) {
            enter(lecternHere ? State.WAIT_JOB : State.PLACE);
        } else {
            enter(State.OPEN_TRADES);
        }
    }

    public void stop(MinecraftClient client, String msg) {
        state = State.IDLE;
        if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
        if (client.player != null) say(client, msg);
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftClient client) {
        if (state == State.IDLE) return;
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null || client.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        CyclerConfig cfg = CyclerConfig.get();

        if (cfg.stopOnDamage && p.hurtTime > 0) {
            stop(client, "You took damage - stopped.");
            return;
        }
        Entity e = w.getEntityById(villagerId);
        if (!(e instanceof VillagerEntity v) || !v.isAlive()) {
            stop(client, "Villager lost.");
            return;
        }
        if (p.distanceTo(v) > 6.0f) {
            stop(client, "Villager too far away.");
            return;
        }

        Screen screen = client.currentScreen;
        if (screen instanceof MerchantScreen) {
            if (state != State.WAIT_OFFERS && state != State.CLOSE_SCREEN) {
                p.closeHandledScreen();
                return;
            }
        } else if (screen != null) {
            return; // some other screen is open: pause
        }

        if (wait > 0) {
            wait--;
            return;
        }
        stateTicks++;

        if (!(screen instanceof MerchantScreen) && age++ % 20 == 0) {
            p.sendMessage(Text.literal("Cycler: " + state + " | tries " + attempts + " | last: " + lastSeen), true);
        }

        switch (state) {
            case OPEN_TRADES -> doOpenTrades(client, p, w, v, cfg);
            case WAIT_OFFERS -> doWaitOffers(client, p, w, cfg);
            case CLOSE_SCREEN -> doCloseScreen(client, p, w, cfg);
            case BREAK -> doBreak(client, p, w);
            case CONFIRM_BREAK -> doConfirmBreak(client, w, cfg);
            case WAIT_PICKUP -> doWaitPickup(client, p);
            case RESTORE -> doRestore(client, p);
            case WAIT_LOSE -> doWaitLose(client, w, v, cfg);
            case PLACE -> doPlace(client, p, w, cfg);
            case CONFIRM_PLACE -> doConfirmPlace(client, w, cfg);
            case WAIT_JOB -> doWaitJob(client, w, v, cfg);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ states

    private void doOpenTrades(MinecraftClient c, ClientPlayerEntity p, ClientWorld w, VillagerEntity v, CyclerConfig cfg) {
        String prof = profession(v);
        if (prof.equals("nitwit")) {
            stop(c, "This villager is a nitwit - it can never get a job.");
            return;
        }
        if (prof.equals("none")) {
            enter(w.getBlockState(lecternPos).isOf(Blocks.LECTERN) ? State.WAIT_JOB : State.PLACE);
            return;
        }
        if (!prof.equals("librarian")) {
            say(c, "Villager is a " + prof + ", not a librarian - recycling.");
            resetCycle(c, w);
            return;
        }
        Vec3d target = v.getBoundingBox().getCenter().add(jx * 0.3, jy * 0.4, jz * 0.3);
        if (!rotateToward(p, target)) {
            onTargetTicks = 0;
            return;
        }
        HitResult h = c.crosshairTarget;
        if (h instanceof EntityHitResult ehr && ehr.getEntity() == v) {
            // Hold the crosshair on the villager for a few ticks first (reaction time + lets the rotation reach the server).
            if (++onTargetTicks < needSteady) return;
            // ONE interaction only. After this we just wait for the screen; we never re-send while waiting.
            ActionResult r = c.interactionManager.interactEntityAtLocation(p, v, ehr, Hand.MAIN_HAND);
            if (!r.isAccepted()) c.interactionManager.interactEntity(p, v, Hand.MAIN_HAND);
            enter(State.WAIT_OFFERS);
            return;
        }
        onTargetTicks = 0;
        if (stateTicks > 80) {
            stop(c, "Cannot aim at the villager (entity reach is 3 blocks, or something blocks the view).");
        } else if (stateTicks % 12 == 0) {
            rerollJitter();
        }
    }

    private void doWaitOffers(MinecraftClient c, ClientPlayerEntity p, ClientWorld w, CyclerConfig cfg) {
        if (!(c.currentScreen instanceof MerchantScreen ms)) {
            // Patient, latency-aware timeout. Nothing is sent while waiting.
            if (stateTicks > 60 + 4 * pingTicks(c)) {
                if (++openRetries > 3) {
                    stop(c, "The trade screen never opened (server lag or the villager is busy).");
                } else {
                    enter(State.OPEN_TRADES);
                    wait = 30 + RNG.nextInt(30) + pingTicks(c);
                }
            }
            return;
        }
        openRetries = 0;
        TradeOfferList offers = ms.getScreenHandler().getRecipes();
        if (offers == null || offers.isEmpty()) {
            if (stateTicks > 100 + 4 * pingTicks(c)) stop(c, "No trades received from the villager.");
            return;
        }
        if (!readingDone) { // "read" the screen for a moment like a person would
            readingDone = true;
            wait = delay(c, cfg);
            return;
        }
        for (TradeOffer o : offers) {
            if (o.getUses() > 0) {
                stop(c, "This villager has already been traded with - its trades are locked and cannot be re-rolled.");
                return;
            }
        }
        attempts++;
        lastSeen = TradeScanner.describe(offers);
        TradeScanner.Match m = TradeScanner.find(offers, cfg.targets);
        if (m != null) {
            state = State.IDLE;
            say(c, "FOUND " + EnchantInfo.pretty(m.id()) + " " + m.level() + " for " + m.price()
                    + " emeralds after " + attempts + " tries. Trade screen left open.");
            if (cfg.playSound) p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return;
        }
        if (attempts >= cfg.maxAttempts) {
            p.closeHandledScreen();
            stop(c, "Reached the max attempts (" + cfg.maxAttempts + ").");
            return;
        }
        enter(State.CLOSE_SCREEN);
        wait = delay(c, cfg);
    }

    private void doCloseScreen(MinecraftClient c, ClientPlayerEntity p, ClientWorld w, CyclerConfig cfg) {
        if (c.currentScreen instanceof MerchantScreen) p.closeHandledScreen();
        resetCycle(c, w);
        wait = delay(c, cfg);
        // Every so often take a longer "human" break.
        if (attempts > 0 && attempts % (18 + RNG.nextInt(12)) == 0) wait += 40 + RNG.nextInt(80);
    }

    private void doBreak(MinecraftClient c, ClientPlayerEntity p, ClientWorld w) {
        if (!w.getBlockState(lecternPos).isOf(Blocks.LECTERN)) {
            c.interactionManager.cancelBlockBreaking();
            // Do NOT continue at once: give the server time to confirm the break (see CONFIRM_BREAK).
            enter(State.CONFIRM_BREAK);
            wait = 6 + 2 * pingTicks(c) + RNG.nextInt(4);
            return;
        }
        if (!p.getMainHandStack().isIn(ItemTags.AXES)) {
            stop(c, "Axe missing from main hand.");
            return;
        }
        BlockHitResult bhr = blockHit(c);
        if (bhr != null && bhr.getBlockPos().equals(lecternPos)) {
            if (!breakingStarted && ++onTargetTicks < needSteady) return; // settle on target before the first hit
            breakingStarted = true;
            if (c.interactionManager.updateBlockBreakingProgress(lecternPos, bhr.getSide())) {
                p.swingHand(Hand.MAIN_HAND);
            }
            return;
        }
        if (breakingStarted) {
            c.interactionManager.cancelBlockBreaking();
            breakingStarted = false;
        }
        onTargetTicks = 0;
        Vec3d target = Vec3d.ofCenter(lecternPos).add(jx * 0.1, jy * 0.2, jz * 0.1);
        rotateToward(p, target);
        if (stateTicks > 240) {
            stop(c, "Cannot break the lectern (out of reach, or the villager blocks the view).");
        } else if (stateTicks % 12 == 0) {
            rerollJitter();
        }
    }

    /** After a break: wait for the server, then make sure the lectern really stayed gone. */
    private void doConfirmBreak(MinecraftClient c, ClientWorld w, CyclerConfig cfg) {
        if (w.getBlockState(lecternPos).isOf(Blocks.LECTERN)) { // the server put it back = break was not registered
            if (++confirmFails > 5) {
                stop(c, "The server keeps rejecting the lectern break (lag, protection, or no permission here).");
            } else {
                enter(State.BREAK);
                wait = delay(c, cfg);
            }
            return;
        }
        confirmFails = 0;
        enter(State.WAIT_PICKUP);
        wait = 4 + RNG.nextInt(4);
    }

    private void doWaitPickup(MinecraftClient c, ClientPlayerEntity p) {
        if (countLecterns(p) > lecternsBefore) {
            enter(p.getOffHandStack().isOf(Items.LECTERN) ? State.WAIT_LOSE : State.RESTORE);
            return;
        }
        if (stateTicks == 100) {
            say(c, "Waiting for you to pick up the dropped lectern - stand next to it.");
        }
        if (stateTicks > 1200) stop(c, "The lectern was never picked up.");
    }

    private void doRestore(MinecraftClient c, ClientPlayerEntity p) {
        int idx = -1;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(Items.LECTERN)) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || ++restoreTries > 4) {
            stop(c, "Could not put a lectern back into your offhand.");
            return;
        }
        int slot = idx < 9 ? 36 + idx : idx; // inventory index -> player screen handler slot
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
        enter(State.WAIT_LOSE);
        wait = 4;
    }

    private void doWaitLose(MinecraftClient c, ClientWorld w, VillagerEntity v, CyclerConfig cfg) {
        if (profession(v).equals("none")) {
            enter(State.PLACE);
            wait = delay(c, cfg);
            return;
        }
        if (stateTicks > 600) {
            stop(c, "The villager kept its job after the lectern was removed (trades locked?).");
        }
    }

    private void doPlace(MinecraftClient c, ClientPlayerEntity p, ClientWorld w, CyclerConfig cfg) {
        if (w.getBlockState(lecternPos).isOf(Blocks.LECTERN)) {
            enter(State.WAIT_JOB);
            return;
        }
        if (!p.getOffHandStack().isOf(Items.LECTERN)) {
            enter(State.RESTORE);
            return;
        }
        Vec3d target = facePoint();
        if (!rotateToward(p, target)) {
            onTargetTicks = 0;
            return;
        }
        BlockHitResult bhr = blockHit(c);
        if (bhr != null && bhr.getBlockPos().equals(supportPos) && bhr.getSide() == face) {
            if (++onTargetTicks < needSteady) return;
            ActionResult r = c.interactionManager.interactBlock(p, Hand.OFF_HAND, bhr);
            if (r.isAccepted()) {
                p.swingHand(Hand.OFF_HAND);
                enter(State.CONFIRM_PLACE);
                wait = 6 + 2 * pingTicks(c) + RNG.nextInt(4);
                return;
            }
        } else {
            onTargetTicks = 0;
        }
        if (stateTicks > 80) {
            stop(c, "Cannot place the lectern (aim blocked, or the spot is occupied).");
        } else if (stateTicks % 12 == 0) {
            rerollJitter();
        }
    }

    /** After a place: wait for the server, then make sure the lectern really stayed. */
    private void doConfirmPlace(MinecraftClient c, ClientWorld w, CyclerConfig cfg) {
        if (!w.getBlockState(lecternPos).isOf(Blocks.LECTERN)) { // reverted by the server
            if (++confirmFails > 5) {
                stop(c, "The server keeps rejecting the lectern placement (lag, protection, or no permission here).");
            } else {
                enter(State.PLACE);
                wait = delay(c, cfg);
            }
            return;
        }
        confirmFails = 0;
        enter(State.WAIT_JOB);
    }

    private void doWaitJob(MinecraftClient c, ClientWorld w, VillagerEntity v, CyclerConfig cfg) {
        if (!w.getBlockState(lecternPos).isOf(Blocks.LECTERN)) {
            if (stateTicks > 10) enter(State.PLACE);
            return;
        }
        String prof = profession(v);
        if (!prof.equals("none")) {
            enter(State.OPEN_TRADES);
            wait = delay(c, cfg);
            return;
        }
        if (stateTicks > 600) {
            stop(c, "The villager did not take the job within 30 s (can it reach the lectern?).");
        }
    }

    // ------------------------------------------------------------------ helpers

    private void enter(State s) {
        state = s;
        stateTicks = 0;
        readingDone = false;
        breakingStarted = false;
        onTargetTicks = 0;
        needSteady = 2 + RNG.nextInt(3);
        rerollJitter();
        if (s == State.BREAK) {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            lecternsBefore = p == null ? 0 : countLecterns(p);
        }
    }

    private void resetCycle(MinecraftClient c, ClientWorld w) {
        if (w.getBlockState(lecternPos).isOf(Blocks.LECTERN)) enter(State.BREAK);
        else enter(State.WAIT_LOSE);
    }

    private void rerollJitter() {
        jx = RNG.nextDouble() - 0.5;
        jy = RNG.nextDouble() - 0.5;
        jz = RNG.nextDouble() - 0.5;
    }

    private static int delay(MinecraftClient c, CyclerConfig cfg) {
        int d = cfg.speed.min + RNG.nextInt(cfg.speed.max - cfg.speed.min + 1) + pingTicks(c);
        if (RNG.nextInt(14) == 0) d += 15 + RNG.nextInt(30); // occasional hesitation
        return d;
    }

    /** Round-trip latency in ticks (1..20), so waits scale with the server's lag. */
    private static int pingTicks(MinecraftClient c) {
        if (c.player == null || c.getNetworkHandler() == null) return 3;
        PlayerListEntry e = c.getNetworkHandler().getPlayerListEntry(c.player.getUuid());
        int ms = e == null ? 100 : e.getLatency();
        return MathHelper.clamp(ms / 50 + 1, 1, 20);
    }

    private static String profession(VillagerEntity v) {
        return Registries.VILLAGER_PROFESSION.getId(v.getVillagerData().getProfession()).getPath();
    }

    private static int countLecterns(ClientPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (p.getInventory().getStack(i).isOf(Items.LECTERN)) n += p.getInventory().getStack(i).getCount();
        }
        return n;
    }

    private static BlockHitResult blockHit(MinecraftClient c) {
        HitResult h = c.crosshairTarget;
        return (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK) ? b : null;
    }

    private Vec3d facePoint() {
        Vec3d center = Vec3d.ofCenter(supportPos);
        Vec3d off = new Vec3d(face.getOffsetX() * 0.49, face.getOffsetY() * 0.49, face.getOffsetZ() * 0.49);
        double ax = face.getAxis() == Direction.Axis.X ? 0 : jx * 0.4;
        double ay = face.getAxis() == Direction.Axis.Y ? 0 : jy * 0.4;
        double az = face.getAxis() == Direction.Axis.Z ? 0 : jz * 0.4;
        return center.add(off).add(ax, ay, az);
    }

    /**
     * Smoothly turns the camera toward a point. The rotation is applied this tick and sent to the server by the
     * normal movement packet next tick, so actions only happen on a later tick (like a real player's).
     * Returns true when already looking at the point.
     */
    private boolean rotateToward(ClientPlayerEntity p, Vec3d point) {
        Vec3d eye = p.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232) - 90.0f;
        float targetPitch = (float) -(MathHelper.atan2(dy, horiz) * 57.29577951308232);
        float dyaw = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dpitch = targetPitch - p.getPitch();
        if (Math.abs(dyaw) < 1.5f && Math.abs(dpitch) < 1.5f) return true;

        float k = 0.40f + RNG.nextFloat() * 0.25f;
        float stepYaw = MathHelper.clamp(dyaw * k, -25f, 25f);
        float stepPitch = MathHelper.clamp(dpitch * k, -18f, 18f);
        p.setYaw(p.getYaw() + stepYaw);
        p.setPitch(MathHelper.clamp(p.getPitch() + stepPitch, -90f, 90f));
        return false;
    }

    private static void say(MinecraftClient c, String msg) {
        if (c.player != null) c.player.sendMessage(Text.literal("[VillagerCycler] " + msg), false);
    }
}

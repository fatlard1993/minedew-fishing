package com.minedew.fishing.encounter;

import com.minedew.fishing.fish.FishMotion;
import com.minedew.fishing.fish.HookedCatch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Server-authoritative state for one fishing encounter, from the moment a fish bites until the
 * player either lands it or it escapes.
 *
 * <p>Two stages, both simulated here at server tick rate:
 *
 * <ol>
 *   <li><b>Hook set.</b> One timing window, opening on the tick the fish bites: {@link Phase#COMMIT}
 *       (vanilla's splash has just played and the bobber is under, so click), then
 *       {@link Phase#GRACE}, a few ticks of network slack after the bobber pops back up. Letting it
 *       lapse loses the bite. There is no overlay for this stage and no telegraph of our own; the
 *       window is vanilla's own {@code nibble} countdown, so it is open exactly while vanilla's own
 *       cues are showing.</li>
 *   <li><b>Fight.</b> The bar game: a fish marker moving under its
 *       {@link com.minedew.fishing.fish.FishMovementPattern}, a bobber the player keeps aloft by
 *       tapping (one click, one upward impulse; gravity does the rest), a catch meter that fills
 *       while the two overlap and drains while they do not, and possibly a treasure chest that
 *       surfaces mid-fight and wants the bobber all to itself.</li>
 * </ol>
 *
 * <p>Positions are track fractions, 0.0 at the bottom and 1.0 at the top. {@link #bobberPosition} is
 * the BOTTOM edge of the bobber, so the covered span is {@code [bobberPosition, bobberPosition +
 * bobberSize]}.
 */
public class FishingEncounter {
    public enum Phase { COMMIT, GRACE, FIGHT }

    public final ServerPlayer player;
    public final int hookEntityId;
    public final HookedCatch hooked;
    public final int difficulty;
    public final RandomSource random;

    // --- Hook set ---

    public Phase phase = Phase.COMMIT;
    public int phaseTicksRemaining;

    // --- Fight ---

    public final FishMotion fish;
    /** Height of the bobber as a track fraction; fixed for the fight, set by difficulty. */
    public final float bobberSize;
    public float bobberPosition;
    public float bobberVelocity;

    /**
     * One click, waiting to be spent by the next tick. A boolean rather than a counter on purpose:
     * it is the "one impulse per tick" ceiling, so mashing past a useful cadence buys nothing and an
     * autoclicker just pins the bobber to the top of the track.
     */
    public boolean impulseQueued;

    public float progress;
    public boolean fishInsideBobber;
    /** Overlap transitions from the most recent {@link #stepFight()}, for the caller's feedback. */
    public boolean justEnteredBobber;
    public boolean justLeftBobber;
    /** Ticks left of drain-free tolerance after the fish slipped out; absorbs input latency. */
    public int drainGraceTicksRemaining;
    /**
     * Ticks the bobber will still hang where the fight started, unless a click ends it first. See
     * {@link MinigameTuning#BOBBER_HOLD_TICKS}.
     */
    public int bobberHeldTicks = MinigameTuning.BOBBER_HOLD_TICKS;

    // --- Treasure ---

    public final boolean hasTreasure;
    public final int treasureAppearsAtTick;
    public final float treasurePosition;
    public boolean treasureVisible;
    public float treasureProgress;
    public boolean treasureSecured;
    /** Set on the tick the chest surfaces, so the caller can reveal it and chime once. */
    public boolean justRevealedTreasure;

    /** Cached because it depends on both the tier and whether this is junk. */
    private final float progressGain;

    public int fightTicks;
    /** Set once an outcome has been resolved so the tick loop knows to drop this entry. */
    public boolean finished;

    /**
     * @param nibbleTicks vanilla's {@code nibble} countdown for this bite, read at the moment it was
     *                    rolled. It is how long the bobber will visibly stay under, and therefore how
     *                    long the hook-set window is open.
     */
    public FishingEncounter(ServerPlayer player, int hookEntityId, HookedCatch hooked,
                            RandomSource random, int nibbleTicks) {
        this.player = player;
        this.hookEntityId = hookEntityId;
        this.hooked = hooked;
        this.difficulty = Mth.clamp(hooked.difficulty(), 1, 4);
        this.random = random;

        this.phase = Phase.COMMIT;
        this.phaseTicksRemaining = MinigameTuning.hookWindowTicks(nibbleTicks);

        this.fish = new FishMotion(hooked, random);
        this.bobberSize = MinigameTuning.bobberSize(this.difficulty);
        this.bobberPosition = 0.5F - this.bobberSize / 2F;
        this.progress = MinigameTuning.PROGRESS_START;
        this.progressGain = MinigameTuning.progressGain(this.difficulty, hooked.species().isJunk());

        // No chest on a boot: junk is meant to be over quickly
        this.hasTreasure = !hooked.species().isJunk() && random.nextFloat() < MinigameTuning.TREASURE_CHANCE;
        this.treasureAppearsAtTick = MinigameTuning.CHEST_APPEAR_MIN_TICKS
            + random.nextInt(MinigameTuning.CHEST_APPEAR_MAX_TICKS - MinigameTuning.CHEST_APPEAR_MIN_TICKS);
        this.treasurePosition = MinigameTuning.CHEST_MIN_POSITION
            + random.nextFloat() * (MinigameTuning.CHEST_MAX_POSITION - MinigameTuning.CHEST_MIN_POSITION);
    }

    // --- Hook set ---

    /** True while a click would set the hook. The window opens with the encounter itself. */
    public boolean inHookSet() {
        return this.phase != Phase.FIGHT;
    }

    /** Advance the hook-set timer. Returns true when the window has lapsed unclicked. */
    public boolean stepHookSet() {
        if (--this.phaseTicksRemaining > 0) return false;

        if (this.phase == Phase.COMMIT) {
            this.phase = Phase.GRACE;
            this.phaseTicksRemaining = MinigameTuning.HOOK_GRACE_TICKS;
            return false;
        }
        return true;
    }

    public void beginFight() {
        this.phase = Phase.FIGHT;
        this.bobberVelocity = 0F;
        this.impulseQueued = false;
    }

    // --- Fight ---

    /**
     * Advance the whole fight one tick, leaving {@link #justEnteredBobber} / {@link #justLeftBobber}
     * / {@link #justRevealedTreasure} set so the caller can play feedback without recomputing.
     *
     * <p>Until the player's first click the fight is only <i>shown</i>, not run: see
     * {@link #stepHeld()}.
     */
    public void stepFight() {
        if (this.bobberHeldTicks > 0 && !this.impulseQueued) {
            stepHeld();
            return;
        }
        this.bobberHeldTicks = 0;
        this.fightTicks++;

        this.fish.step();
        stepBobber();

        boolean wasInside = this.fishInsideBobber;
        this.fishInsideBobber = covers(this.fish.position());

        boolean opening = this.fightTicks <= MinigameTuning.OPENING_FLOOR_TICKS;

        if (this.fishInsideBobber) {
            this.drainGraceTicksRemaining = MinigameTuning.DRAIN_GRACE_TICKS;
            this.progress = Math.min(1F, this.progress + this.progressGain);
        } else if (this.drainGraceTicksRemaining > 0) {
            this.drainGraceTicksRemaining--;
        } else {
            // The opening floor, not an opening freeze: points won early are still losable, so the
            // buffer protects a slow start without banking free progress for a bar nobody is holding
            this.progress = Math.max(opening ? MinigameTuning.PROGRESS_START : 0F,
                this.progress - MinigameTuning.progressDrain(this.difficulty));
        }

        this.justEnteredBobber = !wasInside && this.fishInsideBobber;
        this.justLeftBobber = wasInside && !this.fishInsideBobber;

        stepTreasure();
    }

    /**
     * A tick before the fight has started: the fish swims and the overlay shows it, the bobber hangs
     * where it began, and the clock, the meter and the chest are all stopped.
     *
     * <p>Everything but the fish is frozen on purpose. Holding the bobber alone would hand a player
     * who never touches the rod a bar parked across mid-track, which is the best camping spot there
     * is, and measured a bobber nobody was holding back up to 42% on small cod. Stopping the clock
     * with it means the wait buys nothing at all: no progress, no chest timer, no fight timeout. It
     * only means the fight starts when the player does, from where it was always going to start.
     */
    private void stepHeld() {
        this.bobberHeldTicks--;
        this.fish.step();
        this.fishInsideBobber = covers(this.fish.position());
        this.justEnteredBobber = false;
        this.justLeftBobber = false;
        this.justRevealedTreasure = false;
    }

    private void stepTreasure() {
        this.justRevealedTreasure = false;
        if (!this.hasTreasure || this.treasureSecured) return;

        if (!this.treasureVisible) {
            if (this.fightTicks < this.treasureAppearsAtTick) return;
            this.treasureVisible = true;
            this.justRevealedTreasure = true;
            return;
        }

        float gain = 1F / MinigameTuning.CHEST_CAPTURE_TICKS;
        if (covers(this.treasurePosition)) {
            this.treasureProgress += gain;
            if (this.treasureProgress >= 1F) {
                this.treasureProgress = 1F;
                this.treasureSecured = true;
            }
        } else {
            this.treasureProgress = Math.max(0F,
                this.treasureProgress - gain * MinigameTuning.CHEST_DRAIN_FACTOR);
        }
    }

    private void stepBobber() {
        if (this.bobberHeldTicks > 0 && !this.impulseQueued) {
            // Hanging where the fight started: no gravity, no drift, nothing to recover from
            this.bobberHeldTicks--;
            return;
        }
        this.bobberHeldTicks = 0;

        if (this.impulseQueued) {
            this.impulseQueued = false;
            this.bobberVelocity = Math.max(this.bobberVelocity, -MinigameTuning.CLICK_FALL_ARREST)
                + MinigameTuning.CLICK_IMPULSE;
        }

        this.bobberVelocity -= MinigameTuning.BOBBER_GRAVITY;
        this.bobberVelocity *= MinigameTuning.BOBBER_DAMPING;
        this.bobberVelocity = Mth.clamp(this.bobberVelocity,
            -MinigameTuning.BOBBER_TERMINAL_SPEED, MinigameTuning.BOBBER_TERMINAL_SPEED);
        this.bobberPosition += this.bobberVelocity;

        float ceiling = 1F - this.bobberSize;
        if (this.bobberPosition < 0F) {
            this.bobberPosition = 0F;
            this.bobberVelocity = Math.abs(this.bobberVelocity) * MinigameTuning.BOBBER_BOUNCE;
        } else if (this.bobberPosition > ceiling) {
            this.bobberPosition = ceiling;
            this.bobberVelocity = -Math.abs(this.bobberVelocity) * MinigameTuning.BOBBER_BOUNCE;
        }
    }

    /** True when the bobber's span covers this track position. */
    public boolean covers(float position) {
        return position >= this.bobberPosition && position <= this.bobberPosition + this.bobberSize;
    }

    /**
     * Bobber bottom edge projected forward for rendering only; see
     * {@link MinigameTuning#RENDER_LEAD_TICKS}.
     */
    public float projectedBobberPosition(int ticks) {
        return Mth.clamp(this.bobberPosition + this.bobberVelocity * ticks, 0F, 1F - this.bobberSize);
    }

    public boolean isCaught() {
        return this.progress >= 1F;
    }

    public boolean hasEscaped() {
        return this.progress <= 0F || this.fightTicks >= MinigameTuning.FIGHT_TIMEOUT_TICKS;
    }

    public boolean timedOut() {
        return this.fightTicks >= MinigameTuning.FIGHT_TIMEOUT_TICKS;
    }
}

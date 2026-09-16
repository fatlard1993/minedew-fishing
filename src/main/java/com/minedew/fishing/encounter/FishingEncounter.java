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
    /** The player's, on top of the fish's tier; fixed at the bite so a change lands on the next fish. */
    public final com.minedew.fishing.FishingDifficulty ease;
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

    /** The commit window this bite rolled, kept so the strike can be timed against its opening. */
    private final int hookWindowTicks;

    /**
     * Progress a clean strike opened the meter with, above the usual start; zero for a slow one.
     *
     * <p>Public because the caller says so out loud. The bar visibly starts further along, but not
     * so much further that it reads as anything other than luck unless something names it.
     */
    public float strikeBonus;

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
        this.ease = com.minedew.fishing.FishingDifficulty.of(player);
        this.random = random;

        this.phase = Phase.COMMIT;
        this.hookWindowTicks = MinigameTuning.hookWindowTicks(nibbleTicks);
        this.phaseTicksRemaining = this.hookWindowTicks;

        this.fish = new FishMotion(hooked, random, this.ease);
        // Wider for a run of lost fish; junk is quick either way and gets no kindness.
        int misses = hooked.species().isJunk() ? 0 : FishingStreaks.get(player.level().getServer()).misses(player.getUUID());
        this.bobberSize = Math.min(0.6F, MinigameTuning.bobberSize(this.difficulty) * this.ease.bar
            * MinigameTuning.backoffBarScale(misses));
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
        // How long the bite sat there before it was answered. Inside the commit window that is the
        // part of it already spent; once the window has lapsed into grace the strike was late by
        // definition, and the whole window plus the grace already burnt says so.
        int ticksToStrike = this.phase == Phase.COMMIT
            ? this.hookWindowTicks - this.phaseTicksRemaining
            : this.hookWindowTicks + (MinigameTuning.HOOK_GRACE_TICKS - this.phaseTicksRemaining);
        this.strikeBonus = MinigameTuning.strikeProgressBonus(ticksToStrike);

        // Straight onto the meter. It sits above the opening floor rather than raising it, so a
        // head start that goes unused drains back to where everybody else started.
        this.progress = Math.min(1F, this.progress + this.strikeBonus);

        this.phase = Phase.FIGHT;
        this.bobberVelocity = 0F;
        this.impulseQueued = false;
    }

    // --- Fight ---

    /**
     * Advance the whole fight one tick, leaving {@link #justEnteredBobber} / {@link #justLeftBobber}
     * / {@link #justRevealedTreasure} set so the caller can play feedback without recomputing.
     *
     * <p>Runs from the tick after the hook-set click: that click is the proof of presence, so the
     * fight owes no further wait. (An earlier build held the bar and the clock until a first
     * <i>fight</i> click on top of it, and it read as the game failing to start.) Reaction time is
     * bought by the opening floor instead; see {@link MinigameTuning#OPENING_FLOOR_TICKS}.
     */
    public void stepFight() {
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
                this.progress - MinigameTuning.progressDrain(this.difficulty) * this.ease.drain);
        }

        this.justEnteredBobber = !wasInside && this.fishInsideBobber;
        this.justLeftBobber = wasInside && !this.fishInsideBobber;

        stepTreasure();
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
        if (this.impulseQueued) {
            this.impulseQueued = false;
            this.bobberVelocity = Math.max(this.bobberVelocity, -MinigameTuning.CLICK_FALL_ARREST)
                + MinigameTuning.CLICK_IMPULSE;
        }

        // The fish pulls the line: while it is below the bobber, gravity is multiplied, so a dive
        // drags the bar down after it. Never active while the bobber covers the fish, which is what
        // keeps the hover cadence, and everything tuned around it, untouched.
        float gravity = this.fish.position() < this.bobberPosition
            ? MinigameTuning.BOBBER_GRAVITY * MinigameTuning.FISH_PULL_GRAVITY_MULT
            : MinigameTuning.BOBBER_GRAVITY;
        this.bobberVelocity -= gravity;
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
        return this.progress <= 0F || this.timedOut();
    }

    public boolean timedOut() {
        return this.fightTicks >= MinigameTuning.FIGHT_TIMEOUT_TICKS;
    }
}

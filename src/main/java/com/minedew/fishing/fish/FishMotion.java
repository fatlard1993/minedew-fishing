package com.minedew.fishing.fish;

import com.minedew.fishing.encounter.MinigameTuning;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The marker's vertical motion for one fight, simulated server-side at 20 Hz.
 *
 * <p>Position is a track fraction: 0.0 is the bottom of the track, 1.0 the top. All shaping comes
 * from {@link FishMovementPattern} knobs, scaled by the species' own speed and aggressiveness, by
 * the size class on top of that, and by an overall erraticness factor from the difficulty tier. A
 * small pufferfish and a trophy salmon run the same code and differ only in numbers.
 *
 * <p>Three motion layers, in priority order, all fixed per species and size so they stay learnable:
 *
 * <ol>
 *   <li>the species' primary pattern (cod darts, pufferfish floats, junk snags),</li>
 *   <li>the species' accent, if it has one, for a slice of every cycle (salmon glides between
 *       runs),</li>
 *   <li>the size class's thrash, for the two big classes, which overrides both: that is how a big
 *       one announces itself no matter what species it is.</li>
 * </ol>
 *
 * Only the phase of each cycle is randomised per fight, never which patterns are in play.
 */
public final class FishMotion {
    /** Distance from its target within which a pattern's settle damping applies. */
    private static final float SETTLE_RADIUS = 0.05F;

    private final FishMovementPattern primary;
    private final FishMovementPattern accent;
    private final int accentPeriodTicks;
    private final int accentDurationTicks;
    private final int accentPhase;

    private final FishMovementPattern thrash;
    private final int thrashPeriodTicks;
    private final int thrashDurationTicks;
    private final int thrashPhase;

    private final RandomSource random;
    private final float maxSpeed;
    private final float pullScale;
    private final float jitterScale;

    private FishMovementPattern active;
    private float position;
    private float velocity;
    private float target;
    private int tick;
    private int retargetIn;

    public FishMotion(HookedCatch hooked, RandomSource random) {
        FishSpecies species = hooked.species();
        FishSize size = hooked.size();

        this.primary = species.getPattern();
        this.accent = species.getAccent();
        this.accentPeriodTicks = species.getAccentPeriodTicks();
        this.accentDurationTicks = species.getAccentDurationTicks();
        this.accentPhase = this.accent != null ? random.nextInt(Math.max(1, this.accentPeriodTicks)) : 0;

        // Junk is dead weight, not a fish: it never thrashes no matter what size rolled
        this.thrash = species.isJunk() ? null : size.getThrash();
        this.thrashPeriodTicks = size.getThrashPeriodTicks();
        this.thrashDurationTicks = size.getThrashDurationTicks();
        this.thrashPhase = this.thrash != null ? random.nextInt(Math.max(1, this.thrashPeriodTicks)) : 0;

        this.random = random;
        float speed = species.getBaseSpeed() * (species.isJunk() ? 1F : size.getSpeedScale());
        float aggression = species.getBaseAggressiveness() * (species.isJunk() ? 1F : size.getAggressionScale());
        this.maxSpeed = MinigameTuning.FISH_BASE_MAX_SPEED * speed;
        this.pullScale = aggression;
        this.jitterScale = MinigameTuning.fishErraticScale(hooked.difficulty());

        this.active = this.primary;
        this.position = 0.5F + (random.nextFloat() - 0.5F) * 0.4F;
        this.target = this.position;
        this.retargetIn = rollRetargetDelay();
    }

    /** Advance one server tick. */
    public void step() {
        this.tick++;
        updateActivePattern();
        FishMovementPattern pattern = this.active;

        if (pattern.usesSineSteering()) {
            this.target = pattern.sineCenter
                + Mth.sin(this.tick * pattern.sineRate) * pattern.sineAmplitude;
        } else if (pattern.retargets() && --this.retargetIn <= 0) {
            this.retargetIn = rollRetargetDelay();
            this.target = rollTarget();

            if (pattern.teleportChance > 0F
                && this.random.nextFloat() < pattern.teleportChance * this.pullScale) {
                // It didn't swim there, it's just there now
                this.position = this.target;
                this.velocity = (this.random.nextFloat() - 0.5F) * 0.01F;
            } else if (pattern.burst > 0F) {
                this.velocity += (this.target - this.position) * pattern.burst * this.pullScale;
            }
        }

        float restingTarget = this.target - pattern.targetBias;
        float gap = restingTarget - this.position;
        this.velocity += gap * pattern.pull * this.pullScale;

        if (pattern.jitter > 0F) {
            this.velocity += (this.random.nextFloat() - 0.5F) * pattern.jitter * this.jitterScale;
        }
        if (pattern.reverseChance > 0F && this.random.nextFloat() < pattern.reverseChance) {
            this.velocity *= -1.15F;
        }
        // Arrived: park here rather than drifting through, which is what makes a lunge read as a
        // lunge instead of as constant motion, and what makes a snag read as dead weight
        if (pattern.settleDamping < 1F && Math.abs(gap) < SETTLE_RADIUS) {
            this.velocity *= pattern.settleDamping;
        }

        this.velocity *= MinigameTuning.FISH_DAMPING;
        this.velocity = Mth.clamp(this.velocity, -this.maxSpeed, this.maxSpeed);
        this.position += this.velocity;

        if (this.position < MinigameTuning.FISH_MIN_POSITION) {
            this.position = MinigameTuning.FISH_MIN_POSITION;
            this.velocity = Math.abs(this.velocity) * 0.4F;
        } else if (this.position > MinigameTuning.FISH_MAX_POSITION) {
            this.position = MinigameTuning.FISH_MAX_POSITION;
            this.velocity = -Math.abs(this.velocity) * 0.4F;
        }
    }

    /** Pick which of the three layers has the wheel this tick. */
    private void updateActivePattern() {
        FishMovementPattern wanted = this.primary;
        if (this.accent != null
            && (this.tick + this.accentPhase) % this.accentPeriodTicks < this.accentDurationTicks) {
            wanted = this.accent;
        }
        if (this.thrash != null
            && (this.tick + this.thrashPhase) % this.thrashPeriodTicks < this.thrashDurationTicks) {
            wanted = this.thrash;
        }
        if (wanted == this.active) return;

        this.active = wanted;
        // A layer change re-rolls the retarget clock, so the switch itself reads as a beat
        this.retargetIn = rollRetargetDelay();
    }

    /**
     * Pick somewhere to go that is actually somewhere else.
     *
     * <p>A uniform draw over the whole band is mostly a draw near the middle of it, and a fish that
     * only ever drifts around the middle can be beaten by parking the bobber there and never looking
     * at it: measured at 90-100% for the easy tiers before this rule existed, which is the same
     * "catches are free" failure the rebuild set out to remove. Excluding the
     * {@link MinigameTuning#FISH_MIN_JUMP} band around the current position forces every retarget to
     * be a move worth reacting to, and is also what makes the motion read as lunges rather than ooze.
     */
    private float rollTarget() {
        float lo = MinigameTuning.FISH_MIN_POSITION;
        float hi = MinigameTuning.FISH_MAX_POSITION;
        float jump = MinigameTuning.FISH_MIN_JUMP;

        float below = Math.max(0F, (this.position - jump) - lo);
        float above = Math.max(0F, hi - (this.position + jump));
        if (below + above <= 0F) return this.position - lo > hi - this.position ? lo : hi;

        return this.random.nextFloat() * (below + above) < below
            ? lo + this.random.nextFloat() * below
            : hi - this.random.nextFloat() * above;
    }

    private int rollRetargetDelay() {
        if (!this.active.retargets()) return Integer.MAX_VALUE;
        int span = Math.max(1, this.active.retargetMaxTicks - this.active.retargetMinTicks);
        return this.active.retargetMinTicks + this.random.nextInt(span);
    }

    public float position() {
        return this.position;
    }

    public float velocity() {
        return this.velocity;
    }

    /** True while the size class's thrash has the wheel; only used for HUD flavour. */
    public boolean thrashing() {
        return this.thrash != null && this.active == this.thrash;
    }

    /**
     * Where it will be in {@code ticks} ticks if nothing changes. Used only for rendering: the HUD
     * is pushed one lead ahead so the client's interpolation lands on the true position instead of
     * trailing it (see {@link MinigameTuning#RENDER_LEAD_TICKS}).
     */
    public float projectedPosition(int ticks) {
        return Mth.clamp(this.position + this.velocity * ticks,
            MinigameTuning.FISH_MIN_POSITION, MinigameTuning.FISH_MAX_POSITION);
    }
}

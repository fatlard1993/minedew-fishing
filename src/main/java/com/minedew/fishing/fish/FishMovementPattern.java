package com.minedew.fishing.fish;

/**
 * How something on the end of the line moves up and down the track during the fight, as a set of
 * tuning knobs consumed by {@link FishMotion}. Each constant is meant to read as a distinct
 * personality at a glance rather than as a slightly different oscillation.
 *
 * <p>This enum used to be a dead label: every fish carried one, nothing read it. The behaviour it
 * named lived in a client-side {@code FishBehavior} class that was deleted in the Polymer-to-
 * Pandorical migration, when the minigame briefly became a click-timing game with no moving fish at
 * all. The knobs below are that idea rebuilt as data, but explicitly not that implementation's
 * numbers: those were slow and gentle enough that catches were close to free.
 *
 * <p>The motion is deliberately built out of <b>big readable moves and pauses</b>, not
 * high-frequency noise. Noise is the one thing a networked player cannot fight: it is invisible
 * through a 150 ms round trip, so it reads as unfairness rather than difficulty. A lunge across
 * half the track followed by a settle is hard AND legible.
 *
 * <p>It is also the only thing that stops the game being won by not playing. Every retarget is
 * forced to be a real move by {@code MinigameTuning.FISH_MIN_JUMP}, and every {@code pull} here is
 * strong enough that the fish actually arrives before its next retarget. A pattern whose numbers do
 * not add up to that produces a fish that drifts around mid-track, which is precisely the band a
 * bobber left alone covers: an earlier set of these knobs measured a bobber parked at mid-track
 * landing 90-100% of the two easy tiers.
 *
 * <p>Patterns are assigned per species in {@link FishSpecies} and never rerolled, so they are
 * learnable; {@link FishSize} layers a thrash on top of the bigger classes. Units are track
 * fractions (1.0 = the full height of the track) per tick, at 20 ticks/second.
 */
public enum FishMovementPattern {
    /**
     * A smooth mid-track glide. Nothing sudden ever happens, which is how it gets you.
     *
     * <p>The sweep is bounded above by the fish's own top speed: a sine whose
     * {@code amplitude * rate} exceeds it is not a wide sweep at all, it is a fish permanently
     * chasing a target it cannot reach, which collapses into a slow wobble around the centre. At
     * 0.24 and 0.050 that product is 0.012 against a top speed near 0.018, so the fish stays ahead
     * of its own target.
     *
     * <p>It is bounded below by invariant 2 in {@code MinigameTuning}, which the first cut of this
     * pattern missed. The retarget patterns are all forced to cross at least
     * {@code FISH_MIN_JUMP} of track; a sine is not, and at an amplitude of 0.15 this one swept a
     * 0.30 band centred on 0.50, which is narrower than a tier-1 bobber parked across the middle of
     * it. That is invariant 2's "oozes around mid-track" failure wearing a different hat, and it
     * measured exactly the damage the invariant predicts: salmon was the only species a parked
     * bobber could still beat, at 18-36% where every other species sat under 9%. Widening the sweep
     * so it carries the fish out of a parked bar took that to 7-32% and cost the glide none of its
     * character.
     */
    SLOW_SINUSOIDAL(0.50F, 0.24F, 0.050F, 0, 0, 0.010F, 0.00F, 0.000F, 0.00F, 0.00F, 0.00F, 1.00F),
    /**
     * Sluggish and buoyant: long holds at one depth, an unhurried move to another, riding a little
     * high the whole time. Retarget-driven rather than a sine, so it is guaranteed to actually cover
     * ground between holds instead of bobbing in place.
     */
    SLOW_FLOATER(0.50F, 0F, 0F, 26, 46, 0.025F, 0.30F, 0.000F, 0.00F, 0.00F, -0.04F, 0.50F),
    /** Steady, mid-track, frequent short snaps to a new depth. The baseline personality. */
    MODERATE_DART(0.50F, 0F, 0F, 22, 45, 0.030F, 0.25F, 0.002F, 0.00F, 0.00F, 0.03F, 0.55F),
    /** Hard lunges most of the way across the track, then a real pause before the next run. */
    FAST_DART(0.50F, 0F, 0F, 20, 40, 0.035F, 0.40F, 0.002F, 0.00F, 0.00F, -0.02F, 0.55F),
    /** Skittish: retargets sooner than anything else, with a faint tremor on top. */
    FAST_ERRATIC(0.50F, 0F, 0F, 18, 34, 0.030F, 0.15F, 0.0015F, 0.00F, 0.00F, 0.02F, 0.55F),
    /**
     * Not a fish. Long dead pauses, then a sudden short lurch and a dead stop, the whole time
     * dragging low: a boot catching and releasing on a branch. Nothing about it is smooth, which is
     * the point: a second of this and you know you have hooked garbage.
     */
    SNAG(0.50F, 0F, 0F, 22, 45, 0.060F, 0.50F, 0.000F, 0.00F, 0.00F, 0.06F, 0.30F),
    /** Layered on top of a big fish's own pattern by {@link FishSize}: it thrashes. */
    TROPHY_THRASH(0.50F, 0F, 0F, 12, 24, 0.045F, 0.35F, 0.0035F, 0.05F, 0.00F, 0.00F, 0.70F);

    /** Track position the sine sweep is centered on. */
    public final float sineCenter;
    /** Amplitude of the sine sweep; 0 disables sine steering (timer retargeting takes over). */
    public final float sineAmplitude;
    /** Sine phase advance per tick, in radians. */
    public final float sineRate;
    /** Inclusive lower/upper bound on the random gap between retargets, in ticks. Both 0 = never. */
    public final int retargetMinTicks;
    public final int retargetMaxTicks;
    /** Acceleration toward the current target, as a fraction of the remaining distance, per tick. */
    public final float pull;
    /** One-off impulse toward a freshly picked target, as a fraction of the distance to it. */
    public final float burst;
    /** Random velocity noise applied every tick, +/- half this value. Kept small on purpose. */
    public final float jitter;
    /** Per-tick chance to flip and slightly amplify the current velocity. */
    public final float reverseChance;
    /** Chance, on a retarget, to jump straight to the new depth instead of swimming to it. */
    public final float teleportChance;
    /**
     * How far below its target, in track fractions, it actually settles. Positive sinks, negative
     * floats.
     *
     * <p>Deliberately an offset on the target rather than a constant downward velocity, which is
     * what the pre-migration implementation used. A velocity bias fights the pull term, so the
     * settling offset it produces is {@code bias / (pull * aggressiveness)}: on a sluggish fish that
     * worked out to a third of the whole track, parking the easiest fish permanently in the bottom
     * quarter where a bobber left to sink sat on top of it, and the mod paid out for going AFK.
     * As an offset the sink is bounded and reads the same on everything.
     *
     * <p>Kept small on every pattern for the same reason in the other direction: a fish that camps
     * near either end of the track is covered for free by a bobber resting on the floor or held
     * against the ceiling, and an autoclicker holds the ceiling perfectly. A -0.18 bias here once
     * handed a mashing bot 100% of pufferfish.
     */
    public final float targetBias;
    /**
     * Velocity multiplier applied once it is within 0.05 of its target: below 1.0 it parks there
     * instead of drifting through, which is what turns "moves around" into "holds still, then
     * lunges". {@link #SNAG} takes this to an extreme.
     */
    public final float settleDamping;

    FishMovementPattern(float sineCenter, float sineAmplitude, float sineRate,
                        int retargetMinTicks, int retargetMaxTicks,
                        float pull, float burst, float jitter,
                        float reverseChance, float teleportChance,
                        float targetBias, float settleDamping) {
        this.sineCenter = sineCenter;
        this.sineAmplitude = sineAmplitude;
        this.sineRate = sineRate;
        this.retargetMinTicks = retargetMinTicks;
        this.retargetMaxTicks = retargetMaxTicks;
        this.pull = pull;
        this.burst = burst;
        this.jitter = jitter;
        this.reverseChance = reverseChance;
        this.teleportChance = teleportChance;
        this.targetBias = targetBias;
        this.settleDamping = settleDamping;
    }

    public boolean usesSineSteering() {
        return this.sineAmplitude > 0F;
    }

    public boolean retargets() {
        return this.retargetMaxTicks > 0;
    }
}

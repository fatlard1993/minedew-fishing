package com.minedew.fishing.encounter;

import net.minecraft.util.Mth;

/**
 * Every feel knob for the fishing encounter, in one place.
 *
 * <p>Units are <b>track fractions per tick</b> at 20 ticks/second: 1.0 is the full height of the
 * track, so with a 144 px track a velocity of 0.032 is 4.6 px/tick, or 92 px/second. Keeping the
 * simulation in fractions means the HUD can be re-laid-out (a taller track, a different anchor)
 * without re-tuning the physics.
 *
 * <p>An encounter is two stages. First the <b>hook set</b>: a single telegraphed timing window,
 * where a click during {@link #HOOK_COMMIT_TICKS} (plus {@link #HOOK_GRACE_TICKS} of slack) sets
 * the hook, and clicking early or not at all loses the bite. Then the <b>fight</b>: the bar game,
 * where each click is one upward impulse on the bobber and the catch meter fills only while the
 * bobber covers the fish.
 *
 * <p><b>The primary fight knobs</b> are {@link #CLICK_IMPULSE}, {@link #BOBBER_GRAVITY} and
 * {@link #BOBBER_DAMPING}. The first two set the cadence the game asks for:
 *
 * <pre>neutral cadence (clicks/second) = 20 * BOBBER_GRAVITY / CLICK_IMPULSE</pre>
 *
 * which is the tapping rate that holds the bobber level: about 2 clicks/second at the values below.
 * Tap faster and you climb, ease off and you sink, and the response in between is smooth and linear
 * in cadence rather than an on/off cliff. Raise {@code CLICK_IMPULSE} to make the game ask for
 * lazier tapping, raise {@code BOBBER_GRAVITY} to make it ask for faster tapping.
 *
 * <p><b>Three invariants hold this together.</b> Breaking any of them produces a game that looks
 * fine in the constants and fails in play, which is exactly what the first cut of these numbers did:
 *
 * <ol>
 *   <li><b>The fish must not outrun the bobber.</b> The bound is NOT
 *       {@link #BOBBER_TERMINAL_SPEED}: clicking cannot reach that. Sustained speed for a player
 *       tapping at {@code r} clicks/second is
 *       {@code D/(1-D) * (CLICK_IMPULSE*r/20 - BOBBER_GRAVITY)}, and free fall is
 *       {@code D/(1-D) * BOBBER_GRAVITY}, so the real ceiling on the fastest fish is the sustained
 *       CLIMB rate of a player tapping at a realistic rate. At these values that is about 0.028
 *       climbing and 0.024 falling, against a fastest fish of 0.020.</li>
 *   <li><b>The fish must actually traverse the track.</b> A fish whose top speed cannot cover
 *       {@link #FISH_MIN_JUMP} within its pattern's retarget interval never arrives anywhere: it
 *       oozes around mid-track, and mid-track is exactly what a parked bobber covers. Slow fish are
 *       not easy fish, they are free fish.</li>
 *   <li><b>The meter's break-even duty cycle must sit between the two.</b> The meter breaks even at
 *       {@code drain / (gain + drain)}. Below about 46% a parked bobber wins on its own; above about
 *       52% not even perfect tracking sustains it. Every drain here is derived from a chosen
 *       break-even inside that window, not picked directly.</li>
 * </ol>
 *
 * <p><b>Difficulty is tuned against a headless simulation</b> of the whole fight rather than by eye:
 * an ideal player (no latency, no fumbles, unlimited cadence), a good player (sees the fight 150 ms
 * late, taps up to ~6.7/s, fumbles 8%) and a sloppy player (250 ms, ~5/s, fumbles 25%) each played
 * every species/size 4000 times, alongside a bot that ignores the fish and parks the bobber at the
 * single best fixed height. Measured, for the good player and for the parking bot:
 *
 * <pre>
 *   tier 1: good 78-93%   parked  5-36%    comfortable for a first-timer
 *   tier 2: good 56-86%   parked  0-29%
 *   tier 3: good 25-60%   parked  0-18%    demands attention
 *   tier 4: good  8-43%   parked  0-18%    a real skill check, losable
 * </pre>
 *
 * The spread within a row is across the four species; the parked column is the number that matters
 * most, because an earlier set of these constants let a bobber left alone land 90-100% of the two
 * easy tiers, which are 80% of all catches.
 */
public final class MinigameTuning {
    private MinigameTuning() {}

    // --- Hook set (the timing gate before the bar game) ---

    /** Telegraph before the window opens, by difficulty 1..4. Clicking here loses the bite. */
    private static final int[] HOOK_TELL_TICKS = {12, 10, 9, 8};
    /** The window a click has to land in to set the hook, by difficulty 1..4. */
    private static final int[] HOOK_COMMIT_TICKS = {16, 14, 12, 10};
    /**
     * Slack after the window closes during which a click still counts. This is the hook set's whole
     * latency allowance: a click made on the last frame of the window needs about a round trip to
     * reach the server, and 300 ms covers that comfortably on any sane connection.
     */
    public static final int HOOK_GRACE_TICKS = 6;

    // --- Bobber physics (the three primary feel knobs, plus their shaping) ---

    /** Upward velocity added by one accepted click. See the class doc for the cadence formula. */
    public static final float CLICK_IMPULSE = 0.0210F;
    /** Downward acceleration applied every tick. */
    public static final float BOBBER_GRAVITY = 0.0021F;
    /**
     * Hard cap in both directions. This is a safety rail, not the thing that limits normal play:
     * damping means ordinary clicking settles at a lower sustained speed (see invariant 1 in the
     * class doc). It binds only on a sustained mash, which is what stops an autoclicker from
     * slamming the bobber to the ceiling faster than the fish can be followed.
     */
    public static final float BOBBER_TERMINAL_SPEED = 0.028F;
    /** Velocity retained each tick; the bobber coasts rather than stopping dead. */
    public static final float BOBBER_DAMPING = 0.92F;
    /**
     * A click never has to fight a fall faster than this: the impulse is added on top of a velocity
     * floored at {@code -CLICK_FALL_ARREST}. Only bites during a genuine plummet (roughly half a
     * second of not clicking), so ordinary play is pure "impulse plus gravity", but a missed beat
     * stays recoverable instead of turning into a death spiral.
     */
    public static final float CLICK_FALL_ARREST = 0.018F;
    /** Fraction of velocity kept, reversed, when the bobber hits the top or bottom of the track. */
    public static final float BOBBER_BOUNCE = 0.25F;

    /**
     * Bobber height as a track fraction, by difficulty 1..4: 29 px down to 24 px of the 144 px track.
     *
     * <p>This is the strongest difficulty lever there is, and also the most dangerous one: the bar's
     * height IS how much of the track a player who stops playing covers for free. Anything much
     * above 0.21 makes the easy tiers winnable by parking the bobber mid-track, so the ramp here is
     * deliberately shallow and the rest of the difficulty comes from the fish and the meter.
     */
    private static final float[] BOBBER_SIZE_BY_DIFFICULTY = {0.20F, 0.185F, 0.17F, 0.165F};

    // --- Fish motion ---

    /**
     * Fish speed cap before the species (0.98x to 1.03x) and size (1.00x to 1.05x) scale it, so the
     * fastest combination tops out near 0.020 against a sustained bobber climb of about 0.028 and a
     * fall of about 0.024. A fish that can outrun the bobber is not difficult, it is unfair; a fish
     * too slow to cross the track is not easy, it is free. See invariants 1 and 2 in the class doc.
     */
    public static final float FISH_BASE_MAX_SPEED = 0.018F;
    /** Velocity retained each tick by the fish. */
    public static final float FISH_DAMPING = 0.94F;
    /** The fish stays inside this band, so it is always reachable. */
    public static final float FISH_MIN_POSITION = 0.10F;
    public static final float FISH_MAX_POSITION = 0.90F;
    /** A retarget never lands closer than this to where the fish already is. */
    public static final float FISH_MIN_JUMP = 0.30F;

    /** Multiplier on pattern jitter by difficulty 1..4: the same pattern reads calmer on easy fish. */
    private static final float[] FISH_ERRATIC_BY_DIFFICULTY = {0.70F, 0.85F, 0.95F, 1.05F};

    // --- Catch meter ---

    /**
     * Progress the encounter opens with. Deliberately small: a head start is worth the same to a
     * player who is tracking and to one who is not, so it is the cheapest thing in here to hand a
     * parked bobber, and short fights are where parking pays.
     */
    public static final float PROGRESS_START = 0.10F;
    /**
     * Ticks of unbroken coverage needed to fill the meter from empty, by difficulty 1..4 (3 s to
     * 4.4 s of perfect tracking, and far longer in practice since nobody tracks perfectly).
     *
     * <p>No tier is allowed to be much shorter than this. A short fight is decided by variance
     * rather than by the asymptotic duty cycle, and variance is what a parked bobber needs: cutting
     * these to 45-85 measured a parking bot back up to 47-59% on tier 1.
     */
    private static final int[] CATCH_TICKS_BY_DIFFICULTY = {60, 72, 84, 88};
    /**
     * Junk has no size classes and fights at the easiest tier, but even that would be too long a
     * fight for a boot: it fills faster than any real fish, so hooking garbage is quick and annoying
     * rather than hard. Not faster still, because the same short-fight variance that helps a parked
     * bobber applies here too.
     */
    private static final int JUNK_CATCH_TICKS = 55;
    /**
     * Progress drained per tick while the fish is outside the bobber, by difficulty 1..4.
     *
     * <p>Not picked directly. Each is derived from a chosen break-even duty cycle
     * {@code b = drain / (gain + drain)}, i.e. {@code drain = gain * b / (1 - b)}, with these values
     * putting b at 48%, 48%, 49% and 49%. That window is narrow and both walls are real: measured
     * below about 46%, a bobber parked at mid-track beats the easy tiers on its own; above about
     * 52%, not even a zero-latency player sustains enough coverage and every tier collapses toward
     * zero. Retune the fill times and re-derive these; do not nudge them freely.
     */
    private static final float[] PROGRESS_DRAIN_BY_DIFFICULTY = {0.0154F, 0.0128F, 0.0114F, 0.0109F};
    /**
     * Ticks of drain-free tolerance after the fish slips out of the bobber. This is the deliberate
     * latency allowance for the fight: a click's effect reaches the player's eye roughly 100-150 ms
     * after they press, so the first moments of "outside" are usually the network, not them. Kept
     * short because it is worth more to bursty coverage (a fish crossing a parked bobber) than to
     * the sustained coverage that tracking produces.
     */
    public static final int DRAIN_GRACE_TICKS = 3;
    /** Ticks at the start of the fight during which progress cannot drain at all. */
    public static final int START_GRACE_TICKS = 6;

    // --- Treasure chest ---

    /** Chance a fight carries a treasure chest at all. */
    public static final float TREASURE_CHANCE = 0.20F;
    /** The chest surfaces somewhere in this window after the fight starts. */
    public static final int CHEST_APPEAR_MIN_TICKS = 20;
    public static final int CHEST_APPEAR_MAX_TICKS = 60;
    /**
     * Ticks the bobber must spend covering the chest to secure it: 1.75 s that the bobber is not
     * spending on the fish. Measured against a bot that abandons the fish for the chest the instant
     * it surfaces, this turns a 39% catch on a tier-3 fish into a 13% catch with a 35% chance of
     * keeping the treasure. That bot plays the chest as badly as possible, so treat those as the
     * floor: a player who waits until the fish is already near the chest pays much less.
     */
    public static final int CHEST_CAPTURE_TICKS = 35;
    /** Chest progress lost per tick off the chest, as a fraction of the gain rate. */
    public static final float CHEST_DRAIN_FACTOR = 0.35F;
    /** The chest sits at a fixed spot in this band; it never moves once it has surfaced. */
    public static final float CHEST_MIN_POSITION = 0.15F;
    public static final float CHEST_MAX_POSITION = 0.85F;

    // --- Pacing and networking ---

    /** Hard stop on a fight, in ticks. Reaching it loses the fish. */
    public static final int FIGHT_TIMEOUT_TICKS = 900;
    /**
     * How many ticks ahead of the true simulation the HUD positions are pushed.
     *
     * <p>Pandorical's HUD interpolates each geometry change over {@code INTERPOLATION_TICKS} (3)
     * client ticks. Fed a new value every tick, that filter settles into a steady lag of exactly
     * 3 ticks' worth of motion behind the server, so leading the pushed positions by the same 3
     * ticks cancels it: the smoothing stays, the lag does not.
     */
    public static final int RENDER_LEAD_TICKS = 3;

    public static int hookTellTicks(int difficulty) {
        return HOOK_TELL_TICKS[clampDifficulty(difficulty) - 1];
    }

    public static int hookCommitTicks(int difficulty) {
        return HOOK_COMMIT_TICKS[clampDifficulty(difficulty) - 1];
    }

    public static float bobberSize(int difficulty) {
        return BOBBER_SIZE_BY_DIFFICULTY[clampDifficulty(difficulty) - 1];
    }

    public static float progressGain(int difficulty, boolean junk) {
        return 1F / (junk ? JUNK_CATCH_TICKS : CATCH_TICKS_BY_DIFFICULTY[clampDifficulty(difficulty) - 1]);
    }

    public static float progressDrain(int difficulty) {
        return PROGRESS_DRAIN_BY_DIFFICULTY[clampDifficulty(difficulty) - 1];
    }

    public static float fishErraticScale(int difficulty) {
        return FISH_ERRATIC_BY_DIFFICULTY[clampDifficulty(difficulty) - 1];
    }

    private static int clampDifficulty(int difficulty) {
        return Mth.clamp(difficulty, 1, 4);
    }
}

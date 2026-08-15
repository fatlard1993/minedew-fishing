package com.minedew.fishing.encounter;

import com.minedew.fishing.fish.FishType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Server-authoritative state for one fish-catching encounter (from the moment a fish bites until
 * the player either lands it or it escapes). Every field here lives ONLY on the server; the
 * client never simulates any of this, it only renders what {@code FishingEncounterManager}
 * declares via Pandorical HUD updates.
 */
public class FishingEncounter {
    public enum Phase { TELL, COMMIT, GRACE }

    public final ServerPlayer player;
    public final int hookEntityId;
    public final FishType fishType;
    public final RandomSource random;

    public final int roundsNeeded;
    public final int maxMisses;
    public final int tellTicks;
    public final int commitTicks;

    public final boolean hasTreasure;
    /** 1-based round number that also secures the treasure bonus, or 0 if {@link #hasTreasure} is false. */
    public final int treasureRound;
    public boolean treasureSecured;

    public int currentRound = 0;
    public int hitsCompleted = 0;
    public int missesUsed = 0;

    public Phase phase = Phase.TELL;
    public int ticksRemainingInPhase;
    public int graceTicksRemaining;
    public boolean roundResolved;
    public boolean currentRoundIsFeint;

    public int overallTicksRemaining;
    public int flashFadeTicksRemaining;

    /** Set once an outcome (success/failure) has been resolved so the tick loop knows to drop this entry. */
    public boolean finished;

    public FishingEncounter(ServerPlayer player, int hookEntityId, FishType fishType, RandomSource random,
                             int roundsNeeded, int maxMisses, int tellTicks, int commitTicks,
                             boolean hasTreasure, int treasureRound, int overallTicksRemaining) {
        this.player = player;
        this.hookEntityId = hookEntityId;
        this.fishType = fishType;
        this.random = random;
        this.roundsNeeded = roundsNeeded;
        this.maxMisses = maxMisses;
        this.tellTicks = tellTicks;
        this.commitTicks = commitTicks;
        this.hasTreasure = hasTreasure;
        this.treasureRound = treasureRound;
        this.overallTicksRemaining = overallTicksRemaining;
    }

    public boolean isTreasureRound() {
        return this.hasTreasure && this.currentRound == this.treasureRound;
    }
}

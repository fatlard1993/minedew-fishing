package com.minedew.fishing.access;

/**
 * Duck interface implemented by {@code FishingHookMixin}, letting server code force a real vanilla
 * catch through {@code FishingHook.retrieve()} at the moment our own encounter resolves; see
 * {@code FishingEncounterManager#succeed}.
 *
 * <p>Deliberately lives OUTSIDE the {@code com.minedew.fishing.mixin} package: Sponge Mixin treats
 * every class in a package declared as a mixin config's {@code "package"} as mixin-owned and
 * refuses to let normal application code reference it directly: {@code IllegalClassLoadError} at
 * class-load time, which only surfaces on an actual boot, not at compile time.
 *
 * <p>{@code retrieve()}'s loot-table branch is gated on {@code this.nibble > 0}, not the
 * {@code biting} field its name would suggest. Our own telegraph/commit rounds run for several
 * real seconds, far longer than vanilla's nibble window, so by the time we're ready to let a real
 * catch through, vanilla has almost certainly already decayed {@code nibble} back to 0 on its
 * own; hence forcing it here.
 */
public interface MinedewFishingHookAccess {
    /** Forces the hook back into "a fish is nibbling" state so a subsequent {@code retrieve()} call rolls a real catch. */
    void minedew$forceNibble();
}

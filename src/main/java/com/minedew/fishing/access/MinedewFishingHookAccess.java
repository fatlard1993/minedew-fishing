package com.minedew.fishing.access;

/**
 * Duck interface implemented by {@code FishingHookMixin}, exposing the one piece of vanilla hook
 * state the encounter needs that is otherwise private: the rod's Luck of the Sea contribution, so
 * the mod's own roll of the fishing loot table matches the roll vanilla would have made.
 *
 * <p>Deliberately lives OUTSIDE the {@code com.minedew.fishing.mixin} package: Sponge Mixin treats
 * every class in a package declared as a mixin config's {@code "package"} as mixin-owned and
 * refuses to let normal application code reference it directly: {@code IllegalClassLoadError} at
 * class-load time, which only surfaces on an actual boot, not at compile time.
 */
public interface MinedewFishingHookAccess {
    /** The hook's luck bonus, set from the rod's Luck of the Sea level when it was cast. */
    int minedew$luck();
}

package com.minedew.fishing.mixin;

import com.minedew.fishing.fish.WildFish;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A walleye scooped into a bucket is a walleye when it is poured out. Vanilla carries only a fixed
 * handful of a fish's fields through the bucket, so which fish it is rides along beside them.
 */
@Mixin(AbstractFish.class)
public abstract class AbstractFishMixin {
    @Inject(method = "saveToBucketTag", at = @At("TAIL"))
    private void minedew$keepKind(ItemStack bucket, CallbackInfo ci) {
        WildFish.toBucket((AbstractFish) (Object) this, bucket);
    }

    @Inject(method = "loadFromBucketTag", at = @At("TAIL"))
    private void minedew$restoreKind(CompoundTag tag, CallbackInfo ci) {
        WildFish.fromBucket((AbstractFish) (Object) this, tag);
    }
}

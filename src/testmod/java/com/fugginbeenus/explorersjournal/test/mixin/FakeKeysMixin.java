package com.fugginbeenus.explorersjournal.test.mixin;

import com.fugginbeenus.explorersjournal.test.FakeKeys;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class FakeKeysMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void ejtest$shift(CallbackInfoReturnable<Boolean> cir) {
        if (FakeKeys.shift) cir.setReturnValue(true);
    }

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void ejtest$control(CallbackInfoReturnable<Boolean> cir) {
        if (FakeKeys.control) cir.setReturnValue(true);
    }
}

package com.fugginbeenus.explorersjournal.mixin;

import com.fugginbeenus.explorersjournal.journal.JournalStore;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every world save goes through here: autosave, /save-all, pausing, and shutdown. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void explorersjournal$saveJournals(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        JournalStore.saveWaiting((MinecraftServer) (Object) this);
    }
}

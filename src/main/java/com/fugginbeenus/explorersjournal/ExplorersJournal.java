package com.fugginbeenus.explorersjournal;

import com.fugginbeenus.explorersjournal.config.EJConfig;
import com.fugginbeenus.explorersjournal.journal.JournalService;
import com.fugginbeenus.explorersjournal.journal.JournalStore;
import com.fugginbeenus.explorersjournal.map.MapJob;
import com.fugginbeenus.explorersjournal.net.EJNet;
import com.fugginbeenus.explorersjournal.registry.EJItems;
import com.fugginbeenus.explorersjournal.server.JournalCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class ExplorersJournal implements ModInitializer {
    @Override
    public void onInitialize() {
        // the config file is written at the first start, so a server owner finds it there
        EJConfig.get();
        EJItems.init();
        EJNet.init();
        JournalService.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MapJob.tickAll();
            JournalService.tickPins(server);
        });
        // a player's inventory is saved when they leave, so the journal saves with it
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            JournalStore.saveWaiting(server);
            JournalService.forgetPin(handler.player);
        });
        // the pinned place goes back on Location Tooltip's pill when you come back
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            JournalService.forgetPin(handler.player);
            JournalService.sendPin(handler.player);
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            JournalStore.clear();
            JournalService.clear();
            MapJob.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> JournalCommands.register(dispatcher));
    }
}

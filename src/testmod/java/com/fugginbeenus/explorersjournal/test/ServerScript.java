package com.fugginbeenus.explorersjournal.test;

import com.fugginbeenus.explorersjournal.item.JournalItem;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.journal.JournalStore;
import com.fugginbeenus.explorersjournal.registry.EJItems;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Dedicated server side of the two-player test. */
public final class ServerScript implements DedicatedServerModInitializer {
    private boolean anyoneJoined;
    private boolean bobHasCopy;

    @Override
    public void onInitializeServer() {
        String script = System.getProperty("ej.script", "");
        if (script.isEmpty()) return;
        if (script.equals("pregen")) {
            ServerTickEvents.END_SERVER_TICK.register(this::pregen);
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            anyoneJoined = true;
            ServerPlayer player = handler.player;
            if ((script.startsWith("mp1") || script.equals("mp3")) && player.getGameProfile().getName().equals("Alice")) {
                player.getInventory().clearContent();
                player.getInventory().add(new ItemStack(EJItems.JOURNAL));
                if (script.equals("mp3")) player.getInventory().add(new ItemStack(Items.POPPY, 3));
                if (script.equals("mp1sketch")) player.getInventory().add(new ItemStack(Items.POPPY));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (script.startsWith("mp1") || script.equals("mp3")) giveBobACopy(server);
            if (script.equals("mp3")) slowDown();
            if (anyoneJoined && server.getPlayerList().getPlayerCount() == 0) {
                Report.line("server stopping, nobody left");
                server.halt(false);
                anyoneJoined = false;
            }
        });
    }

    private final java.util.List<net.minecraft.world.level.ChunkPos> toMake = new java.util.ArrayList<>(), forced = new java.util.ArrayList<>();
    private int made, waited;

    /** Makes the ground the scripts visit, around the village, so a new world for another version is ready for them. A few chunks at a time, the way a player loads them. Then it stops. */
    private void pregen(MinecraftServer server) {
        var level = server.overworld();
        if (made == 0 && toMake.isEmpty()) {
            for (int cz = -50 >> 4; cz <= 450 >> 4; cz++) {
                for (int cx = -1200 >> 4; cx <= -350 >> 4; cx++) toMake.add(new net.minecraft.world.level.ChunkPos(cx, cz));
            }
            Report.line("making " + toMake.size() + " chunks");
        }
        if (!forced.isEmpty()) {
            boolean ready = forced.stream().allMatch(p -> level.getChunkSource().getChunkNow(p.x, p.z) != null);
            if (!ready && ++waited < 2400) return;
            if (!ready) Report.line("some chunks were slow, going on");
            for (var p : forced) level.setChunkForced(p.x, p.z, false);
            made += forced.size();
            forced.clear();
            waited = 0;
        }
        if (toMake.isEmpty()) {
            server.saveAllChunks(true, true, true);
            Report.line("made " + made + " chunks");
            server.halt(false);
            return;
        }
        for (int i = 0; i < 32 && !toMake.isEmpty(); i++) {
            var p = toMake.remove(0);
            level.setChunkForced(p.x, p.z, true);
            forced.add(p);
        }
    }

    /** A server under load: about 6 ticks a second. */
    private static void slowDown() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void giveBobACopy(MinecraftServer server) {
        if (bobHasCopy) return;
        ServerPlayer alice = server.getPlayerList().getPlayerByName("Alice");
        ServerPlayer bob = server.getPlayerList().getPlayerByName("Bob");
        if (alice == null || bob == null) return;
        for (int i = 0; i < alice.getInventory().getContainerSize(); i++) {
            ItemStack stack = alice.getInventory().getItem(i);
            if (!stack.is(EJItems.JOURNAL) || JournalItem.id(stack) == null) continue;
            Journal journal = JournalStore.get(server, JournalItem.id(stack));
            if (journal != null && !journal.entries.isEmpty()) {
                bob.getInventory().clearContent();
                bob.getInventory().add(stack.copy());
                if (System.getProperty("ej.script", "").equals("mp3")) bob.getInventory().add(new ItemStack(Items.POPPY, 2));
                bobHasCopy = true;
                Report.line("gave Bob a copy of Alice's journal");
            }
        }
    }
}

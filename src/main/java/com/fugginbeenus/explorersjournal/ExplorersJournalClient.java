package com.fugginbeenus.explorersjournal;

import com.fugginbeenus.explorersjournal.client.JournalScreen;
import com.fugginbeenus.explorersjournal.client.MapImages;
import com.fugginbeenus.explorersjournal.client.PinnedPlace;
import com.fugginbeenus.explorersjournal.compat.LocationTooltipCompat;
import com.fugginbeenus.explorersjournal.client.Viewfinder;
import com.fugginbeenus.explorersjournal.item.JournalItem;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.mixin.ItemPropertiesInvoker;
import com.fugginbeenus.explorersjournal.net.EJPayloads;
import com.fugginbeenus.explorersjournal.net.client.EJNetClient;
import com.fugginbeenus.explorersjournal.registry.EJItems;
import com.fugginbeenus.explorersjournal.util.EJId;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

public final class ExplorersJournalClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EJNetClient.init();
        EJNetClient.registerReceiver(EJPayloads.OPEN_JOURNAL, (client, msg) ->
                client.setScreen(new JournalScreen(Journal.fromNet(msg.json()), msg)));
        EJNetClient.registerReceiver(EJPayloads.JOURNAL_DATA, (client, msg) -> {
            if (Minecraft.getInstance().screen instanceof JournalScreen screen) {
                screen.receive(Journal.fromNet(msg.json()), msg);
            }
        });
        EJNetClient.registerReceiver(EJPayloads.MAP_IMAGE, (client, msg) -> MapImages.receive(msg.mapId(), msg.packed()));
        EJNetClient.registerReceiver(EJPayloads.PIN_INFO, (client, msg) -> PinnedPlace.set(msg.name(), msg.dim(), msg.x(), msg.z()));
        if (LocationTooltipCompat.linked()) LocationTooltipCompat.addPill(PinnedPlace::label, PinnedPlace::icon);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            MapImages.clear();
            JournalScreen.clearArt();
            PinnedPlace.set("", "", 0, 0);
        }));
        ClientTickEvents.START_CLIENT_TICK.register(Viewfinder::tick);
        HudRenderCallback.EVENT.register((ctx, tick) -> Viewfinder.draw(ctx));
        // The property has to stay in 0 to 1, so the model reads stage / 4.
        ItemPropertiesInvoker.explorersjournal$register(EJItems.JOURNAL, EJId.of("wear"),
                (stack, level, entity, seed) -> JournalItem.wear(stack) / 4f);
    }
}

package com.fugginbeenus.explorersjournal.registry;

import com.fugginbeenus.explorersjournal.item.JournalItem;
import com.fugginbeenus.explorersjournal.util.EJId;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class EJItems {
    public static final Item JOURNAL = Registry.register(BuiltInRegistries.ITEM, EJId.of("journal"),
            new JournalItem(new Item.Properties().stacksTo(1)));

    private EJItems() {}

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(JOURNAL));
    }
}

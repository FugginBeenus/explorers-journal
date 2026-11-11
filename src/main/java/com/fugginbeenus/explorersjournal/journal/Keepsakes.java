package com.fugginbeenus.explorersjournal.journal;

import com.fugginbeenus.explorersjournal.registry.EJItems;
import com.fugginbeenus.explorersjournal.util.EJId;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** What can go in a journal, and how an item becomes text and back. */
public final class Keepsakes {
    private static final int MAX_DATA = 8192;
    private static final TagKey<Item> CANNOT_PASTE = TagKey.create(Registries.ITEM, EJId.of("cannot_paste"));

    private Keepsakes() {}

    /** Things that hold other things stay out, and so does anything too big to send with the pages. */
    public static boolean canPaste(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty() || stack.is(EJItems.JOURNAL) || stack.is(CANNOT_PASTE) || stack.getItem() instanceof BundleItem) return false;
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return false;
        return write(stack.copyWithCount(1), registries).length() <= MAX_DATA;
    }

    public static String write(ItemStack stack, HolderLookup.Provider registries) {
        //? if >=1.21 {
        /*return stack.save(registries).toString();
        *///?} else {
        return stack.save(new CompoundTag()).toString();
        //?}
    }

    /** Empty if the item's mod is gone. The text stays in the journal, so the item comes back with the mod. */
    public static ItemStack read(String data, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = TagParser.parseTag(data);
            //? if >=1.21 {
            /*return ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
            *///?} else {
            return ItemStack.of(tag);
            //?}
        } catch (CommandSyntaxException e) {
            return ItemStack.EMPTY;
        }
    }
}

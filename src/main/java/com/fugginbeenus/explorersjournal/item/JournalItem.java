package com.fugginbeenus.explorersjournal.item;

import com.fugginbeenus.explorersjournal.journal.JournalService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public class JournalItem extends Item {
    private static final String ID = "journal_id";
    private static final String WEAR = "wear";
    private static final String ENTRIES = "entries";
    private static final String OWNER = "owner_name";

    public JournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            JournalService.open(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static String id(ItemStack stack) {
        CompoundTag tag = read(stack);
        return tag.contains(ID) ? tag.getString(ID) : null;
    }

    public static int wear(ItemStack stack) {
        return read(stack).getInt(WEAR);
    }

    public static void store(ItemStack stack, String id, int wear, int entries, String ownerName) {
        CompoundTag tag = read(stack);
        tag.putString(ID, id);
        tag.putInt(WEAR, wear);
        tag.putInt(ENTRIES, entries);
        tag.putString(OWNER, ownerName);
        write(stack, tag);
    }

    private static CompoundTag read(ItemStack stack) {
        //? if >=1.21 {
        /*return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        *///?} else {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
        //?}
    }

    private static void write(ItemStack stack, CompoundTag tag) {
        //? if >=1.21 {
        /*stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        *///?} else {
        stack.setTag(tag);
        //?}
    }

    //? if >=1.21 {
    /*@Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        addLines(stack, tooltip);
    }
    *///?} else {
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        addLines(stack, tooltip);
    }
    //?}

    private static void addLines(ItemStack stack, List<Component> tooltip) {
        CompoundTag tag = read(stack);
        if (!tag.contains(ID)) return;
        tooltip.add(Component.literal("by " + tag.getString(OWNER)).withStyle(ChatFormatting.GRAY));
        int entries = tag.getInt(ENTRIES);
        tooltip.add(Component.literal(entries + (entries == 1 ? " entry" : " entries")).withStyle(ChatFormatting.GRAY));
    }
}

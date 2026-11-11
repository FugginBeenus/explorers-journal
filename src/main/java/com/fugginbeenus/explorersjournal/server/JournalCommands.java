package com.fugginbeenus.explorersjournal.server;

import com.fugginbeenus.explorersjournal.config.EJConfig;
import com.fugginbeenus.explorersjournal.item.JournalItem;
import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.journal.JournalService;
import com.fugginbeenus.explorersjournal.journal.JournalStore;
import com.fugginbeenus.explorersjournal.registry.EJItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** /journal, for server operators. */
public final class JournalCommands {
    private static final int PAGE = 10;

    private JournalCommands() {}

    private record Found(ItemStack stack, Journal journal) {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("journal")
                .requires(source -> source.hasPermission(2))
                // how worn the book in your hand looks, so all the art can be seen. auto goes back to its real wear.
                .then(Commands.literal("wear")
                        .then(Commands.literal("auto").executes(ctx -> wear(ctx, -1)))
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, Journal.STAGES - 1))
                                .executes(ctx -> wear(ctx, IntegerArgumentType.getInteger(ctx, "stage")))))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(JournalCommands::remove))))
                .then(Commands.literal("reload").executes(JournalCommands::reload)));
    }

    private static int wear(CommandContext<CommandSourceStack> ctx, int stage) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        Journal journal = stack.is(EJItems.JOURNAL) ? JournalStore.get(player.level().getServer(), JournalItem.id(stack)) : null;
        if (journal == null) {
            ctx.getSource().sendFailure(Component.literal("Hold a journal you have opened once."));
            return 0;
        }
        journal.wearOverride = stage;
        JournalService.commit(player.level().getServer(), player, stack, journal);
        ctx.getSource().sendSuccess(() -> Component.literal(stage < 0 ? "The journal wears by itself again." : "Wear stage set to " + stage + "."), false);
        return 1;
    }

    /** The journal a player has in hand, or else the first one they carry. */
    private static Found journalOf(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            Found found = found(player, player.getItemInHand(hand));
            if (found != null) return found;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            Found found = found(player, player.getInventory().getItem(slot));
            if (found != null) return found;
        }
        return null;
    }

    private static Found found(ServerPlayer player, ItemStack stack) {
        if (!stack.is(EJItems.JOURNAL) || JournalItem.id(stack) == null) return null;
        Journal journal = JournalStore.get(player.level().getServer(), JournalItem.id(stack));
        return journal == null ? null : new Found(stack, journal);
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Found found = journalOf(player);
        if (found == null) {
            ctx.getSource().sendFailure(Component.literal(player.getGameProfile().getName() + " carries no journal that has been opened."));
            return 0;
        }
        Journal journal = found.journal();
        int pages = Math.max(1, (journal.entries.size() + PAGE - 1) / PAGE);
        int shown = Math.min(page, pages);
        String owner = journal.ownerName.isBlank() ? "someone" : journal.ownerName;
        ctx.getSource().sendSuccess(() -> Component.literal("Journal of " + owner + ", held by " + player.getGameProfile().getName()
                + ": " + journal.entries.size() + " entries, page " + shown + " of " + pages), false);
        for (int i = (shown - 1) * PAGE; i < Math.min(journal.entries.size(), shown * PAGE); i++) {
            String line = (i + 1) + ". " + describe(journal.entries.get(i));
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return journal.entries.size();
    }

    private static String describe(Entry entry) {
        String kind = entry.isLocation() ? "place" : entry.isSketch() ? "sketch" : entry.isDrawing() ? "drawing" : Entry.SCRAPBOOK.equals(entry.type) ? "scrapbook" : "note";
        String title = entry.title.isBlank() ? "(no title)" : entry.title;
        return title + ", " + kind + ", day " + entry.day + (entry.keepsakes.isEmpty() ? "" : ", " + entry.keepsakes.size() + " taped in");
    }

    /** Takes one entry out of the journal a player carries. What was taped in goes back to that player. */
    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int number = IntegerArgumentType.getInteger(ctx, "number");
        Found found = journalOf(player);
        if (found == null) {
            ctx.getSource().sendFailure(Component.literal(player.getGameProfile().getName() + " carries no journal that has been opened."));
            return 0;
        }
        Journal journal = found.journal();
        if (number > journal.entries.size()) {
            ctx.getSource().sendFailure(Component.literal("That journal has " + journal.entries.size() + " entries. See /journal list " + player.getGameProfile().getName() + "."));
            return 0;
        }
        Entry entry = journal.entries.get(number - 1);
        String line = number + ". " + describe(entry);
        JournalService.removeEntry(player.level().getServer(), player, found.stack(), journal, entry);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + line), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        String problem = EJConfig.reload();
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Explorer's Journal config reloaded."), true);
        return 1;
    }
}

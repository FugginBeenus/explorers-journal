package com.fugginbeenus.explorersjournal.test;

import com.fugginbeenus.explorersjournal.client.JournalScreen;
import com.fugginbeenus.explorersjournal.client.MapImages;
import com.fugginbeenus.explorersjournal.client.Section;
import com.fugginbeenus.explorersjournal.client.SketchJob;
import com.fugginbeenus.explorersjournal.client.Viewfinder;
import com.fugginbeenus.explorersjournal.item.JournalItem;
import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.journal.JournalService;
import com.fugginbeenus.explorersjournal.journal.JournalStore;
import com.fugginbeenus.explorersjournal.journal.Keepsake;
import com.fugginbeenus.explorersjournal.journal.Keepsakes;
import com.fugginbeenus.explorersjournal.net.EJPayloads;
import com.fugginbeenus.explorersjournal.net.client.EJNetClient;
import com.fugginbeenus.explorersjournal.registry.EJItems;
import com.fugginbeenus.explorersjournal.client.DrawingDesk;
import com.fugginbeenus.explorersjournal.sketch.PixelCanvas;
import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import com.fugginbeenus.explorersjournal.sketch.SketchRays;
import com.fugginbeenus.explorersjournal.util.EJId;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
//? if >=1.21 {
/*import net.minecraft.world.item.crafting.CraftingInput;
*///?}
import net.minecraft.world.item.crafting.RecipeType;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Plays the game by script: gives items, clicks pages, types, takes screenshots, and writes pass or fail lines. */
public final class ClientScript implements ClientModInitializer {
    private record Step(Predicate<Minecraft> ready, Consumer<Minecraft> action) {}

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private int waitTicks;

    @Override
    public void onInitializeClient() {
        String script = System.getProperty("ej.script", "");
        if (script.isEmpty()) return;
        switch (script) {
            case "stage0" -> stage0();
            case "note" -> note();
            case "noteAgain" -> noteAgain();
            case "alice1" -> alice1();
            case "bob1" -> bob1();
            case "alice2" -> alice2();
            case "place" -> place();
            case "nav" -> nav();
            case "placeAgain" -> placeAgain();
            case "alice3" -> alice3();
            case "bob3" -> bob3();
            case "typing" -> typing();
            case "controls" -> controls();
            case "paste" -> paste();
            case "sketchSheet" -> sketchSheet();
            case "sketch" -> sketch();
            case "sketchMobs" -> sketchMobs();
            case "sketchPlaces" -> sketchPlaces();
            case "sketchSky" -> sketchSky();
            case "drawing" -> drawing();
            case "drawingAgain" -> drawingAgain();
            case "photo" -> photo();
            case "crash1" -> crash1();
            case "crash2" -> crash2();
            case "crash3" -> crash3();
            case "crash4" -> crash4();
            case "alice4" -> alice4();
            case "bob4" -> bob4();
            case "alice5" -> alice5();
            case "bob5" -> bob5();
            case "layouts" -> layouts();
            case "notes" -> notes();
            case "wear" -> wear();
            case "link" -> link();
            case "linkOld" -> linkOld();
            case "rules" -> rules();
            default -> Report.line("no such script: " + script);
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(script.equals("sketchPlaces") ? 2_400_000 : 600_000);
            } catch (InterruptedException e) {
                return;
            }
            Report.line("FAIL script timed out, " + steps.size() + " steps left");
            Runtime.getRuntime().halt(1);
        });
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void tick(Minecraft mc) {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        Step step = steps.peek();
        if (step != null && step.ready.test(mc)) {
            steps.poll();
            step.action.accept(mc);
        }
    }

    private void act(Consumer<Minecraft> action) {
        steps.add(new Step(mc -> true, action));
    }

    private void until(Predicate<Minecraft> ready) {
        steps.add(new Step(ready, mc -> {}));
    }

    private void pause(int ticks) {
        act(mc -> waitTicks = ticks);
    }

    private void inWorld() {
        until(mc -> mc.player != null && mc.level != null);
        // another window taking focus must not pause the game under the script
        act(mc -> mc.options.pauseOnLostFocus = false);
        pause(60);
    }

    /** Turns through every spread once, so each thing on every page has been drawn and knows its real shape. */
    private void flipAll() {
        act(mc -> {
            while (screen(mc).spread() > 0) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(2);
        for (int i = 0; i < 110; i++) {
            act(mc -> keyQuiet(mc, GLFW.GLFW_KEY_RIGHT));
            pause(2);
        }
        pause(5);
    }

    private void command(String command) {
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            String name = mc.player.getGameProfile().getName();
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.getPlayerList().getPlayerByName(name).createCommandSourceStack().withPermission(4).withSuppressedOutput(), command));
        });
        pause(5);
    }

    private void shot(String name) {
        act(mc -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> Report.line("screenshot " + name)));
        pause(5);
    }

    private void openJournal() {
        act(mc -> {
            var inv = mc.player.getInventory();
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(EJItems.JOURNAL)) inv.selected = i;
            }
        });
        pause(5);
        act(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        until(mc -> mc.screen instanceof JournalScreen);
        pause(10);
    }

    private void closeScreen() {
        act(mc -> mc.screen.onClose());
        pause(10);
    }

    private void finish() {
        act(mc -> Report.line("script done"));
        pause(10);
        act(Minecraft::stop);
    }

    private static int bookX(Minecraft mc) {
        return (mc.screen.width - 312) / 2;
    }

    private static int bookY(Minecraft mc) {
        return Math.max(8, (mc.screen.height - 196 - 24) / 2);
    }

    private void clickBook(int dx, int dy, int button) {
        act(mc -> mc.screen.mouseClicked(bookX(mc) + dx, bookY(mc) + dy, button));
        pause(5);
    }

    private void redRibbon() {
        clickBook(180, 205, 0);
    }

    private void clickTab(Section section) {
        clickBook(312, section.y + 8, 0);
    }

    /** Goes to the next blank page and clicks a choice there. */
    private void clickChoice(int index) {
        redRibbon();
        act(mc -> {
            int x = screen(mc).blankPage() % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + 30, bookY(mc) + 16 + 40 + index * 16 + 4, 0);
        });
        pause(5);
    }

    /** Clicks on an entry's page, at this spot on the page. */
    private void clickEntry(java.util.function.Function<Minecraft, String> id, int dx, int dy) {
        act(mc -> {
            int page = screen(mc).pageOfEntry(id.apply(mc));
            int x = page % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + dx, bookY(mc) + 16 + dy, 0);
        });
        pause(5);
    }

    /** Clicks a spot the book gives, from its top left. */
    private void clickSpot(java.util.function.Function<JournalScreen, int[]> spot) {
        act(mc -> {
            int[] at = spot.apply(screen(mc));
            mc.screen.mouseClicked(bookX(mc) + at[0], bookY(mc) + at[1], 0);
            mc.screen.mouseReleased(bookX(mc) + at[0], bookY(mc) + at[1], 0);
        });
        pause(8);
    }

    /** Clicks an entry's title, wherever its layout put it. */
    private void clickTitleOf(java.util.function.Function<JournalScreen, String> id) {
        clickSpot(s -> s.spotsOf(id.apply(s)));
    }

    private void typeText(String text) {
        act(mc -> {
            for (char c : text.toCharArray()) mc.screen.charTyped(c, 0);
        });
        pause(2);
    }

    private void key(int keyCode) {
        act(mc -> mc.screen.keyPressed(keyCode, 0, 0));
        pause(2);
    }

    private void writeNote(String title, String body) {
        clickChoice(writeNoteRow());
        typeText(title);
        key(GLFW.GLFW_KEY_ENTER);
        typeText(body);
    }

    private int drawRow() {
        return 2;
    }

    private int writeNoteRow() {
        return 3;
    }

    private int pasteRow() {
        return 4;
    }

    // ---- step 0: the item crafts, opens, and every wear stage draws

    private void stage0() {
        inWorld();
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            //? if >=1.21 {
            /*var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.COMPASS)));
            server.execute(() -> {
                var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server.overworld());
                boolean ok = recipe.isPresent() && recipe.get().id().equals(EJId.of("journal"))
                        && recipe.get().value().assemble(input, server.registryAccess()).is(EJItems.JOURNAL);
                Report.check("Book and Quill + Compass crafts a journal", ok);
            });
            *///?} else {
            var input = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
                @Override
                public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                    return false;
                }
            }, 2, 1);
            input.setItem(0, new ItemStack(Items.WRITABLE_BOOK));
            input.setItem(1, new ItemStack(Items.COMPASS));
            server.execute(() -> {
                var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server.overworld());
                boolean ok = recipe.isPresent() && recipe.get().getId().equals(EJId.of("journal"))
                        && recipe.get().assemble(input, server.registryAccess()).is(EJItems.JOURNAL);
                Report.check("Book and Quill + Compass crafts a journal", ok);
            });
            //?}
        });
        command("clear @a");
        command("give @a explorersjournal:journal");
        pause(10);
        openJournal();
        act(mc -> Report.check("the journal opens a book screen", mc.screen instanceof JournalScreen));
        shot("stage0_new");
        closeScreen();
        for (int stage = 1; stage < 5; stage++) {
            command("journal wear " + stage);
            pause(10);
            shot("item_stage" + stage);
            openJournal();
            shot("stage" + stage);
            closeScreen();
        }
        command("journal wear auto");
        finish();
    }

    // ---- step 1 in singleplayer: write a note, close, reopen

    private void note() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        pause(10);
        openJournal();
        writeNote("Who owes me", "The smith, for a good stick. Also a chicken.");
        shot("note_typing");
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        act(mc -> Report.check("Esc stops writing and keeps the book open", mc.screen instanceof JournalScreen));
        pause(20);
        shot("note_written");
        closeScreen();
        pause(20);
        openJournal();
        shot("note_reopened");
        closeScreen();
        pause(40);
        finish();
    }

    private void noteAgain() {
        inWorld();
        openJournal();
        shot("note_after_restart");
        act(mc -> Report.line("journal screen is open after restart"));
        closeScreen();
        finish();
    }

    // ---- step 2: mark a place, see the map, write on the page, walk away, turn around

    private static JournalScreen screen(Minecraft mc) {
        return (JournalScreen) mc.screen;
    }

    private void place() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 0 0");
        pause(100);
        openJournal();
        clickChoice(0);
        act(mc -> Report.line("clicked mark this place"));
        shot("place_drawing");
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 1);
        pause(40);
        act(mc -> {
            var entry = screen(mc).journal().entries.get(0);
            Report.line("title: " + entry.title + ", day " + entry.day + ", at " + entry.x + " " + entry.y + " " + entry.z + ", map " + entry.map);
            Report.check("the title matches Location Tooltip's name for this village", entry.title.equals("Stonehollow"));
            Report.check("it is a location entry with a map", entry.isLocation() && entry.map != null);
        });
        shot("place_new");
        clickEntry(mc -> screen(mc).journal().entries.get(0).id, 10, 148);
        typeText("Good water, bad bread. Safe to sleep here.");
        shot("place_typing");
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(20);
        act(mc -> Report.check("the note is on the location page", screen(mc).journal().entries.get(0).text.startsWith("Good water")));
        command("tp @s -700 80 300 0 0");
        pause(40);
        shot("place_far_facing_south");
        act(mc -> mc.player.setYRot(90));
        pause(5);
        shot("place_far_facing_west");
        act(mc -> mc.player.setYRot(180));
        pause(5);
        shot("place_far_facing_north");
        closeScreen();
        pause(20);
        finish();
    }

    private void placeAgain() {
        inWorld();
        openJournal();
        pause(40);
        shot("place_after_restart");
        act(mc -> Report.check("the entry is still there after a restart", !screen(mc).journal().entries.isEmpty()));
        closeScreen();
        finish();
    }

    private void alice3() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(100);
        openJournal();
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 1);
        pause(40);
        shot("alice_map");
        act(mc -> Report.check("alice's map entry arrived from the dedicated server", screen(mc).journal().entries.get(0).isLocation()));
        closeScreen();
        act(mc -> Report.line("alice marked a place, waiting for bob"));
        until(mc -> online(mc) == 2);
        until(mc -> online(mc) == 1);
        pause(20);
        finish();
    }

    private void bob3() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        pause(40);
        shot("bob_sees_map");
        closeScreen();
        finish();
    }

    // ---- step 3: moving through a real journal with no buttons

    /** Adds entries straight on the server, the same way real ones are stored. */
    private void addEntries(int notes, int places) {
        act(mc -> {
            String id = JournalItem.id(mc.player.getMainHandItem());
            String name = mc.player.getGameProfile().getName();
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                Journal journal = JournalStore.get(server, id);
                String map = journal.entries.stream().filter(e -> e.map != null).map(e -> e.map).findFirst().orElse(null);
                for (int i = 0; i < notes; i++) {
                    Entry e = new Entry();
                    e.id = UUID.randomUUID().toString();
                    e.day = 2 + i;
                    e.title = "Note number " + i;
                    e.text = "Written on a long road. The smith says hello number " + i + ".";
                    journal.entries.add(e);
                }
                for (int i = 0; i < places; i++) {
                    Entry e = new Entry();
                    e.id = UUID.randomUUID().toString();
                    e.type = Entry.LOCATION;
                    e.day = 40 + i;
                    e.title = "Camp " + i;
                    e.dim = "minecraft:overworld";
                    e.x = -976 + 300 * (i + 1);
                    e.y = 70;
                    e.z = 150 - 120 * i;
                    e.map = map;
                    journal.entries.add(e);
                }
                var player = server.getPlayerList().getPlayerByName(name);
                JournalService.commit(server, player, player.getMainHandItem(), journal);
            });
        });
        pause(10);
    }

    private static int firstPageOfKind(JournalScreen s, String kind) {
        for (int i = 0; i < s.pageCount(); i++) {
            if (s.pageKind(i).equals(kind)) return i;
        }
        return -1;
    }

    private void checkSpreadShows(String what, java.util.function.Function<Minecraft, Integer> page) {
        act(mc -> Report.check(what, screen(mc).spread() == page.apply(mc) / 2));
    }

    private void nav() {
        String longText = "We left the river at first light and followed the old road north. ".repeat(14);
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();
        act(mc -> Report.check("a new journal opens on the contents page", screen(mc).pageKind(0).equals("CONTENTS") && screen(mc).spread() == 0));
        shot("nav_new_contents");

        // the ribbon goes to the blank page, which offers the choices
        redRibbon();
        checkSpreadShows("the red ribbon goes to the next blank page", mc -> screen(mc).blankPage());
        shot("nav_blank_page");
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 1);
        pause(30);
        checkSpreadShows("a new place shows its page", mc -> screen(mc).pageOfEntry(screen(mc).journal().entries.get(0).id));
        shot("nav_first_place");

        // a note with a link back to the place
        writeNote("Who owes me", "The smith, for a good stick.");
        key(GLFW.GLFW_KEY_ENTER);
        typeText("see: Stonehollow");
        shot("nav_note_typing");
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(20);
        shot("nav_note_with_link");
        act(mc -> Report.check("the note is saved", screen(mc).journal().entries.size() == 2));
        // the link is on the note's second line
        act(mc -> {
            var entries = screen(mc).journal().entries;
            int page = screen(mc).pageOfEntry(entries.get(1).id);
            int x = page % 2 == 0 ? 17 : 166;
            // jump away first if the place and the note share a spread, the link still has to work
            mc.screen.mouseClicked(bookX(mc) + x + 20, bookY(mc) + 16 + 13 + 25, 0);
        });
        pause(5);
        checkSpreadShows("the link goes to Stonehollow", mc -> screen(mc).pageOfEntry(screen(mc).journal().entries.get(0).id));

        // a fuller journal: lists flow onto more pages
        addEntries(16, 12);
        act(mc -> mc.screen.onClose());
        pause(5);
        openJournal();
        act(mc -> {
            JournalScreen s = screen(mc);
            Report.line("pages now: " + s.pageCount() + ", contents pages start " + firstPageOfKind(s, "CONTENTS") + ", places start " + firstPageOfKind(s, "PLACES") + ", entries start " + firstPageOfKind(s, "ENTRY"));
            Report.check("the contents runs on to a second page", s.pageKind(1).equals("CONTENTS"));
        });
        // back to the front by scrolling
        act(mc -> {
            while (screen(mc).spread() > 0) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(5);
        act(mc -> Report.check("the arrow keys reach the first page", screen(mc).spread() == 0));
        shot("nav_contents_full");
        // a line in the contents jumps to its entry
        clickBook(17 + 30, 16 + 13 + 11 + 4, 0);
        act(mc -> Report.check("a contents line opens an entry", screen(mc).spread() > 0));
        shot("nav_contents_click");
        act(mc -> scroll(mc, -1));
        pause(5);
        shot("nav_after_scroll_down");

        // the place list, nearest first
        act(mc -> {
            JournalScreen s = screen(mc);
            int places = firstPageOfKind(s, "PLACES");
            while (s.spread() > places / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < places / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        shot("nav_places");

        // find
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = firstPageOfKind(s, "PLACES") % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + 10, bookY(mc) + 167, 0);
        });
        pause(5);
        typeText("number 7");
        pause(5);
        shot("nav_find");
        key(GLFW.GLFW_KEY_ENTER);
        pause(5);
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = firstPageOfKind(s, "PLACES") % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + 30, bookY(mc) + 16 + 26 + 4, 0);
        });
        pause(5);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry want = s.journal().entries.stream().filter(e -> e.title.equals("Note number 7")).findFirst().orElse(null);
            Report.check("find leads to the right page", want != null && s.spread() == s.pageOfEntry(want.id) / 2);
        });

        // clicking a result while still in the find line lets go of it, so keys turn pages again
        act(mc -> {
            JournalScreen s = screen(mc);
            int places = firstPageOfKind(s, "PLACES");
            while (s.spread() > places / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            mc.screen.mouseClicked(bookX(mc) + (places % 2 == 0 ? 17 : 166) + 10, bookY(mc) + 167, 0);
        });
        pause(5);
        act(mc -> {
            int x = firstPageOfKind(screen(mc), "PLACES") % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + 30, bookY(mc) + 16 + 26 + 4, 0);
        });
        pause(5);
        final int[] atResult = new int[1];
        act(mc -> atResult[0] = screen(mc).spread());
        key(GLFW.GLFW_KEY_RIGHT);
        pause(3);
        act(mc -> Report.check("keys turn pages after clicking away from find", screen(mc).spread() == atResult[0] + 1));
        key(GLFW.GLFW_KEY_LEFT);
        pause(3);

        // the place list is longer again without the search, but you stay on your writing
        final String[] onLeft = new String[1];
        act(mc -> onLeft[0] = screen(mc).entryOnPage(screen(mc).spread() * 2));
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(3);
        act(mc -> {
            JournalScreen s = screen(mc);
            Report.check("ending the search keeps the same writing open", !onLeft[0].isEmpty()
                    && (onLeft[0].equals(s.entryOnPage(s.spread() * 2)) || onLeft[0].equals(s.entryOnPage(s.spread() * 2 + 1))));
        });

        // tabs go through their section
        clickTab(Section.LOCATIONS);
        act(mc -> {
            JournalScreen s = screen(mc);
            Report.check("the L tab opens the place list", s.pageKind(s.spread() * 2).equals("PLACES") || s.pageKind(s.spread() * 2 + 1).equals("PLACES"));
        });
        clickTab(Section.LOCATIONS);
        act(mc -> Report.check("the L tab then opens the first place", screen(mc).spread() == screen(mc).pageOfEntry(screen(mc).journal().entries.get(0).id) / 2));
        clickTab(Section.NOTES);
        act(mc -> Report.check("the N tab opens the first note", screen(mc).spread() == screen(mc).pageOfEntry(screen(mc).journal().entries.get(1).id) / 2));
        clickTab(Section.CONTENTS);
        act(mc -> Report.check("the C tab opens the contents", screen(mc).spread() == 0));
        clickTab(Section.NOTES);
        pause(5);
        shot("nav_tabs");

        // a bookmark: right-click the red ribbon, go away, click the blue ribbon
        final int[] marked = new int[1];
        act(mc -> marked[0] = screen(mc).spread());
        clickBook(130, 198, 0);
        pause(10);
        act(mc -> Report.check("the blue ribbon marks the page", !screen(mc).journal().bookmark.isEmpty()));
        redRibbon();
        shot("nav_blue_ribbon");
        clickBook(130, 205, 0);
        act(mc -> Report.check("the blue ribbon comes back to the marked page", screen(mc).spread() == marked[0]));
        clickBook(130, 205, 0);
        pause(10);
        act(mc -> Report.check("the blue ribbon comes out on its own page", screen(mc).journal().bookmark.isEmpty()));

        // a long note flows over several pages, and the book follows the caret
        final int[] before = new int[1];
        act(mc -> before[0] = screen(mc).pageCount());
        writeNote("The long road", longText);
        pause(5);
        act(mc -> {
            Report.check("a long note takes more pages", screen(mc).pageCount() >= before[0] + 2);
            Report.line("spread while writing " + screen(mc).spread() + ", pages " + screen(mc).pageCount());
        });
        shot("nav_long_note_typing");
        final int[] writingAt = new int[1];
        act(mc -> writingAt[0] = screen(mc).spread());
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(20);
        act(mc -> {
            Entry last = screen(mc).journal().entries.get(screen(mc).journal().entries.size() - 1);
            Report.check("the whole long note is saved", last.text.length() == longText.stripTrailing().length());
            Report.check("saving a long note stays on the same page", screen(mc).spread() == writingAt[0]);
        });
        shot("nav_long_note_end");
        act(mc -> scroll(mc, 1));
        pause(5);
        shot("nav_long_note_start");

        // a note emptied out is gone
        final int[] count = new int[1];
        act(mc -> count[0] = screen(mc).journal().entries.size());
        writeNote("Scrap", "x");
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(20);
        act(mc -> Report.check("one more note", screen(mc).journal().entries.size() == count[0] + 1));
        clickEntry(mc -> screen(mc).journal().entries.get(screen(mc).journal().entries.size() - 1).id, 20, 15);
        key(GLFW.GLFW_KEY_BACKSPACE);
        key(GLFW.GLFW_KEY_TAB);
        for (int i = 0; i < 8; i++) key(GLFW.GLFW_KEY_BACKSPACE);
        key(GLFW.GLFW_KEY_ESCAPE);
        pause(20);
        act(mc -> Report.check("a note emptied out is gone", screen(mc).journal().entries.size() == count[0]));

        // the book reopens where you left it, and the art changes with wear
        final int[] left = new int[1];
        act(mc -> left[0] = screen(mc).spread());
        closeScreen();
        command("journal wear 4");
        pause(5);
        openJournal();
        act(mc -> Report.check("the book reopens at the same page", screen(mc).spread() == left[0]));
        act(mc -> {
            while (screen(mc).spread() > 0) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(5);
        shot("nav_battered_contents");
        closeScreen();
        command("journal wear auto");
        finish();
    }

    // ---- step 1 on a dedicated server with two players

    private static boolean hasJournal(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(EJItems.JOURNAL)) return true;
        }
        return false;
    }

    private static int online(Minecraft mc) {
        return mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
    }

    private void alice1() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        writeNote("Who owes me", "The smith, for a good stick.");
        pause(10);
        closeScreen();
        act(mc -> Report.line("alice wrote her note, waiting for bob"));
        until(mc -> online(mc) == 2);
        until(mc -> online(mc) == 1);
        pause(20);
        finish();
    }

    private void bob1() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        shot("bob_reads_alices_journal");
        // Bob is not the owner: his client shows no writing, and a forged packet must do nothing.
        act(mc -> {
            String id = com.fugginbeenus.explorersjournal.item.JournalItem.id(mc.player.getMainHandItem());
            EJNetClient.send(EJPayloads.SAVE_ENTRY, new EJPayloads.SaveEntry(id, UUID.randomUUID().toString(), "Bob was here", "forged"));
            Report.line("bob sent a forged save");
        });
        pause(40);
        closeScreen();
        finish();
    }

    private void alice2() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        shot("alice_after_server_restart");
        closeScreen();
        finish();
    }

    // ---- text input, through the game's own keyboard code

    private static void keyEvent(Minecraft mc, int key, int action) throws Exception {
        Method method = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(mc.keyboardHandler, mc.getWindow().getWindow(), key, GLFW.glfwGetKeyScancode(key), action, 0);
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t;
    }

    private void press(int key) {
        pressWith(key, false, false);
    }

    private void pressWith(int key, boolean shift, boolean control) {
        act(mc -> {
            FakeKeys.shift = shift;
            FakeKeys.control = control;
            try {
                keyEvent(mc, key, GLFW.GLFW_PRESS);
                keyEvent(mc, key, GLFW.GLFW_RELEASE);
            } catch (Throwable t) {
                Report.check("key " + key + " works, it threw " + rootCause(t), false);
            } finally {
                FakeKeys.shift = false;
                FakeKeys.control = false;
            }
        });
        pause(1);
    }

    private void type(String text) {
        act(mc -> {
            try {
                Method method = KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, int.class, int.class);
                method.setAccessible(true);
                for (char c : text.toCharArray()) method.invoke(mc.keyboardHandler, mc.getWindow().getWindow(), (int) c, 0);
            } catch (Throwable t) {
                Report.check("typing works, it threw " + rootCause(t), false);
            }
        });
        pause(1);
    }

    private void check(String what, Predicate<JournalScreen> test) {
        act(mc -> {
            try {
                Report.check(what, mc.screen instanceof JournalScreen s && test.test(s));
            } catch (Throwable t) {
                Report.check(what + ", it threw " + rootCause(t), false);
            }
        });
    }

    private void clip(String text) {
        act(mc -> mc.keyboardHandler.setClipboard(text));
    }

    private static int pageLeft(int page) {
        return page % 2 == 0 ? 17 : 166;
    }

    /** Clicks a spot on the page of the note being written. */
    private void clickWriting(int dx, int dy) {
        act(mc -> {
            int x = pageLeft(screen(mc).editingPage());
            mc.screen.mouseClicked(bookX(mc) + x + dx, bookY(mc) + 16 + dy, 0);
            mc.screen.mouseReleased(bookX(mc) + x + dx, bookY(mc) + 16 + dy, 0);
        });
        pause(8);
    }

    private void newNote(String title, String body) {
        clickChoice(writeNoteRow());
        type(title);
        press(GLFW.GLFW_KEY_ENTER);
        type(body);
    }

    private void endWriting() {
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(15);
    }

    private void typing() {
        final int enter = GLFW.GLFW_KEY_ENTER, esc = GLFW.GLFW_KEY_ESCAPE, tab = GLFW.GLFW_KEY_TAB;
        final int up = GLFW.GLFW_KEY_UP, down = GLFW.GLFW_KEY_DOWN, left = GLFW.GLFW_KEY_LEFT, right = GLFW.GLFW_KEY_RIGHT;
        final int home = GLFW.GLFW_KEY_HOME, end = GLFW.GLFW_KEY_END;
        final String[] oldClip = new String[1];

        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();
        act(mc -> oldClip[0] = mc.keyboardHandler.getClipboard());

        // a space that lands right at the end of a full line
        newNote("Wrap test", "xxxxxxxxxxxxxxxxxxxx ");
        check("the caret is found after a space at the end of a full line", s -> s.caretLine() >= 0);
        shot("typing_wrap_space");
        press(up);
        check("up works after that space", s -> s.focusName().equals("body") || s.focusName().equals("title"));
        endWriting();
        newNote("Wrap test two", "xxxxxxxxxxxxxxxxxxxx ");
        press(home);
        press(end);
        check("home and end work after that space", s -> s.caretLine() >= 0);
        endWriting();

        // typing plainly, then in the middle, through the real keyboard path
        newNote("Plain", "alpha beta gamma");
        check("typed text arrives", s -> s.bodyText().equals("alpha beta gamma"));
        clickWriting(3, 13 + 4);
        type("Z");
        check("a click puts the caret at the start of the line", s -> s.bodyText().equals("Zalpha beta gamma"));
        press(GLFW.GLFW_KEY_BACKSPACE);
        check("backspace works", s -> s.bodyText().equals("alpha beta gamma"));

        // the mouse selects text
        clickWriting(3, 13 + 4);
        act(mc -> {
            FakeKeys.shift = true;
            int x = pageLeft(screen(mc).editingPage());
            mc.screen.mouseClicked(bookX(mc) + x + 3 + 26, bookY(mc) + 16 + 13 + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 3 + 26, bookY(mc) + 16 + 13 + 4, 0);
            FakeKeys.shift = false;
        });
        pause(8);
        check("shift and click selects text", s -> !s.selectedText().isEmpty());
        clickWriting(3, 13 + 4);
        act(mc -> {
            int x = pageLeft(screen(mc).editingPage()), y = bookY(mc) + 16 + 13 + 4;
            mc.screen.mouseClicked(bookX(mc) + x + 3, y, 0);
            mc.screen.mouseDragged(bookX(mc) + x + 3 + 40, y, 0, 40, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 3 + 40, y, 0);
        });
        pause(2);
        check("dragging the mouse selects text", s -> !s.selectedText().isEmpty());
        shot("typing_drag_selection");
        pause(8);
        act(mc -> {
            int x = pageLeft(screen(mc).editingPage()), y = bookY(mc) + 16 + 13 + 4;
            mc.screen.mouseClicked(bookX(mc) + x + 3 + 10, y, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 3 + 10, y, 0);
            mc.screen.mouseClicked(bookX(mc) + x + 3 + 10, y, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 3 + 10, y, 0);
        });
        pause(1);
        check("a double click selects the word", s -> s.selectedText().equals("alpha"));
        pause(8);
        act(mc -> {
            int x = pageLeft(screen(mc).editingPage()), y = bookY(mc) + 16 + 13 + 4;
            for (int i = 0; i < 3; i++) {
                mc.screen.mouseClicked(bookX(mc) + x + 3 + 10, y, 0);
                mc.screen.mouseReleased(bookX(mc) + x + 3 + 10, y, 0);
            }
        });
        pause(1);
        check("a triple click selects it all", s -> s.selectedText().equals("alpha beta gamma"));

        // copy, cut, paste, and undo with the shortcut keys
        pressWith(GLFW.GLFW_KEY_C, false, true);
        act(mc -> Report.check("copy works", mc.keyboardHandler.getClipboard().equals("alpha beta gamma")));
        pressWith(GLFW.GLFW_KEY_X, false, true);
        check("cut works", s -> s.bodyText().isEmpty());
        pressWith(GLFW.GLFW_KEY_V, false, true);
        check("paste works", s -> s.bodyText().equals("alpha beta gamma"));
        pressWith(GLFW.GLFW_KEY_A, false, true);
        type("q");
        check("typing over a selection replaces it", s -> s.bodyText().equals("q"));
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("undo brings the text back", s -> s.bodyText().equals("alpha beta gamma"));
        pressWith(GLFW.GLFW_KEY_Z, true, true);
        check("redo works", s -> s.bodyText().equals("q"));
        clip("no§color");
        pressWith(GLFW.GLFW_KEY_V, false, true);
        check("paste drops colour codes", s -> !s.bodyText().contains("§"));
        endWriting();

        // the title is a real text field too
        clickChoice(writeNoteRow());
        check("a new note starts in the title", s -> s.focusName().equals("title"));
        type("Hello");
        clickWriting(3, 2);
        check("a click in the title moves its caret", s -> s.focusName().equals("title") && s.caret() == 0);
        press(right);
        type("Z");
        check("the title types where its caret is", s -> s.titleText().equals("HZello"));
        shot("typing_title_caret");
        pressWith(left, true, false);
        check("shift and arrow selects in the title", s -> s.selectedText().equals("Z"));
        shot("typing_title_selection");
        press(GLFW.GLFW_KEY_DELETE);
        press(GLFW.GLFW_KEY_KP_ENTER);
        check("numpad enter does not break the title", s -> s.titleText().equals("Hello") && s.focusName().equals("body"));
        press(tab);
        clip("one\ntwo");
        pressWith(GLFW.GLFW_KEY_V, false, true);
        check("pasted lines stay on one title line", s -> !s.titleText().contains("\n"));
        pressWith(GLFW.GLFW_KEY_A, false, true);
        type("i".repeat(30));
        check("a title is limited by its width, not 18 letters", s -> s.titleText().length() > 18);
        pressWith(GLFW.GLFW_KEY_A, false, true);
        type("W".repeat(30));
        act(mc -> Report.check("a title stays on the page", mc.font.width(Component.literal(screen(mc).titleText()).withStyle(ChatFormatting.BOLD)) <= 129));
        press(down);
        check("down goes from the title to the writing", s -> s.focusName().equals("body"));
        press(up);
        check("up goes from the first line back to the title", s -> s.focusName().equals("title"));
        endWriting();

        // opening a place with a long name must not cut the name
        act(mc -> {
            String id = JournalItem.id(mc.player.getMainHandItem());
            String name = mc.player.getGameProfile().getName();
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                Journal journal = JournalStore.get(server, id);
                Entry e = new Entry();
                e.id = UUID.randomUUID().toString();
                e.type = Entry.LOCATION;
                e.day = 3;
                e.title = "Windswept Gravelly Hills";
                e.dim = "minecraft:overworld";
                e.x = -900;
                e.y = 70;
                e.z = 100;
                journal.entries.add(e);
                var player = server.getPlayerList().getPlayerByName(name);
                JournalService.commit(server, player, player.getMainHandItem(), journal);
            });
        });
        pause(10);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry place = s.journal().entries.get(s.journal().entries.size() - 1);
            while (s.spread() < s.pageOfEntry(place.id) / 2) keyQuiet(mc, right);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(s.pageOfEntry(place.id)) + 3 + 2, bookY(mc) + 16 + 140 + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(s.pageOfEntry(place.id)) + 3 + 2, bookY(mc) + 16 + 140 + 4, 0);
        });
        pause(8);
        check("clicking a place's note starts writing there", s -> s.focusName().equals("body"));
        type("Nice view");
        shot("typing_place_note");
        endWriting();
        check("a long place name is not cut by writing a note", s -> s.journal().entries.get(s.journal().entries.size() - 1).title.equals("Windswept Gravelly Hills"));
        shot("typing_place_long_title");

        // a very long note
        newNote("Long", "");
        clip("word ".repeat(1100));
        pressWith(GLFW.GLFW_KEY_V, false, true);
        pause(2);
        check("a note stops at the size limit", s -> s.bodyText().length() == 4000);
        act(mc -> Report.line("long note: spread " + screen(mc).spread() + ", caret page " + screen(mc).caretPage()));
        check("the book follows the caret to the end of a pasted note", s -> s.caretPage() / 2 == s.spread());
        final int[] saved = new int[1];
        act(mc -> saved[0] = screen(mc).bodyText().stripTrailing().length());
        endWriting();
        pause(10);
        act(mc -> {
            var entries = screen(mc).journal().entries;
            Report.check("a note at the size limit is saved whole", entries.get(entries.size() - 1).text.length() == saved[0]);
        });

        // the caret stays in view when you move between title and writing
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(s.journal().entries.size() - 1).id);
            while (s.spread() > page / 2) keyQuiet(mc, left);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(page) + 3, bookY(mc) + 16 + 2, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(page) + 3, bookY(mc) + 16 + 2, 0);
        });
        pause(8);
        check("clicking the title of a long note goes to the title", s -> s.focusName().equals("title"));
        press(tab);
        check("tab shows the page with the caret", s -> s.focusName().equals("body") && s.caretPage() / 2 == s.spread());
        pressWith(tab, true, false);
        check("tab goes back and shows the title", s -> s.focusName().equals("title") && s.editingPage() / 2 == s.spread());
        endWriting();

        // find is a real text field too
        act(mc -> {
            JournalScreen s = screen(mc);
            int places = firstPageOfKind(s, "PLACES");
            while (s.spread() > places / 2) keyQuiet(mc, left);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(places) + 10, bookY(mc) + 170, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(places) + 10, bookY(mc) + 170, 0);
        });
        pause(5);
        check("clicking find starts a search", s -> s.focusName().equals("find"));
        clip("Pl\nain");
        pressWith(GLFW.GLFW_KEY_V, false, true);
        check("pasted lines stay on one find line", s -> !s.findText().contains("\n"));
        press(left);
        type("Y");
        shot("typing_find_caret");
        check("find types where its caret is", s -> s.findText().endsWith("Yn"));
        press(esc);
        pause(3);

        // a trackpad sends many small scrolls, and a flick must not skip through the book
        final int[] at = new int[1];
        act(mc -> {
            at[0] = screen(mc).spread();
            for (int i = 0; i < 20; i++) scroll(mc, -0.2);
        });
        pause(1);
        check("small trackpad scrolls turn at most one page", s -> s.spread() <= at[0] + 1);
        pause(10);
        act(mc -> {
            at[0] = screen(mc).spread();
            for (int i = 0; i < 20; i++) scroll(mc, 1);
        });
        pause(1);
        check("a fast flick turns one page, not twenty", s -> Math.abs(s.spread() - at[0]) <= 1);
        pause(10);

        // while you write, the book stays with you
        newNote("Stay here", "words");
        act(mc -> scroll(mc, -1));
        pause(1);
        check("scrolling does not stop your writing", s -> s.focusName().equals("body"));
        act(mc -> {
            mc.screen.mouseClicked(2, 2, 0);
            mc.screen.mouseReleased(2, 2, 0);
        });
        pause(10);
        act(mc -> Report.check("a click outside the book stops writing, but the book stays open", mc.screen instanceof JournalScreen s && s.focusName().isEmpty()));
        act(mc -> {
            var entries = screen(mc).journal().entries;
            Report.check("that click saved the note", entries.get(entries.size() - 1).title.equals("Stay here"));
        });

        // Esc steps back one level at a time
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(s.journal().entries.size() - 1).id);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(page) + 3 + 2, bookY(mc) + 16 + 13 + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(page) + 3 + 2, bookY(mc) + 16 + 13 + 4, 0);
        });
        pause(8);
        check("clicking your writing starts writing", s -> s.focusName().equals("body"));
        press(esc);
        pause(5);
        act(mc -> Report.check("the first Esc stops writing", mc.screen instanceof JournalScreen s && s.focusName().isEmpty()));
        press(esc);
        pause(5);
        act(mc -> Report.check("the second Esc closes the book", mc.screen == null));

        act(mc -> mc.keyboardHandler.setClipboard(oldClip[0]));
        finish();
    }

    // ---- auto sketch: the same views as the Python test sheet, so the two can be compared

    private SketchJob sheetJob;

    private void sketchView(String name, double x, double eyeY, double z, float yaw, float pitch) {
        act(mc -> mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, eyeY - 1.62, z, yaw, pitch)));
        pause(100);
        // the test sheet has only blocks
        command("kill @e[type=!minecraft:player]");
        pause(30);
        act(mc -> {
            Screenshot.grab(mc.gameDirectory, "place_" + name + ".png", mc.getMainRenderTarget(), message -> {});
            sheetJob = new SketchJob(mc.level, x, eyeY, z, yaw, pitch, 62, 3);
        });
        until(mc -> {
            sheetJob.work(5_000_000);
            return sheetJob.done();
        });
        act(mc -> {
            try {
                Files.createDirectories(mc.gameDirectory.toPath().resolve("sketches"));
                Files.write(mc.gameDirectory.toPath().resolve("sketches/" + name + ".lv"), sheetJob.image());
                saveSheetJob(mc.gameDirectory.toPath().resolve("sketches"), name);
            } catch (IOException e) {
                Report.check("the sketch could be saved, it threw " + e, false);
            }
        });
    }

    private void clickInWorld() {
        act(mc -> KeyMapping.click(mc.options.keyAttack.getDefaultKey()));
        pause(3);
    }

    private void sketch() {
        final int esc = GLFW.GLFW_KEY_ESCAPE;
        inWorld();
        command("gamemode creative");
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -996 67 122 -121 -6");
        pause(100);
        openJournal();
        hover(-40, -6);
        shot("sketch_blank_page");

        // Sketch this view: the book steps aside and the frame shows
        final int[] blocksHere = new int[1];
        clickChoice(1);
        pause(10);
        act(mc -> Report.check("the book steps aside for the viewfinder", mc.screen == null && Viewfinder.isOpen()));
        shot("sketch_viewfinder");

        // Esc means never mind
        act(mc -> mc.pauseGame(false));
        pause(4);
        act(mc -> Report.check("Esc goes back to the book", mc.screen instanceof JournalScreen && !Viewfinder.isOpen()));
        check("Esc starts nothing", s -> s.journal().entries.isEmpty() && s.focusName().isEmpty());

        // the slot stays, and a click in the world hits nothing
        clickChoice(1);
        pause(5);
        act(mc -> {
            mc.player.getInventory().selected = (mc.player.getInventory().selected + 1) % 9;
            BlockHitResult look = mc.hitResult instanceof BlockHitResult b ? b : null;
            blocksHere[0] = look == null ? 0 : BuiltInRegistries.BLOCK.getId(mc.level.getBlockState(look.getBlockPos()).getBlock());
        });
        pause(2);
        act(mc -> Report.check("the journal stays in hand in the viewfinder", mc.player.getMainHandItem().is(EJItems.JOURNAL)));
        act(mc -> KeyMapping.click(mc.options.keyDrop.getDefaultKey()));
        pause(5);
        act(mc -> Report.check("the drop key does not drop the journal in the viewfinder", mc.player.getMainHandItem().is(EJItems.JOURNAL)));
        clickInWorld();
        act(mc -> Report.check("a click takes the sketch and opens the book", mc.screen instanceof JournalScreen));
        check("the page shows it is still sketching, or it is done", s -> s.sketchInProgress() || s.sketchReady());
        shot("sketch_drawing");
        until(mc -> mc.screen instanceof JournalScreen s && s.sketchReady());
        pause(25);
        shot("sketch_ready");
        act(mc -> {
            var look = mc.hitResult instanceof BlockHitResult b ? mc.level.getBlockState(b.getBlockPos()).getBlock() : null;
            Report.check("the click did not break anything", look == null || BuiltInRegistries.BLOCK.getId(look) == blocksHere[0] || blocksHere[0] == 0);
        });

        // Retry goes back to the viewfinder, and keeps what you wrote
        type("The first house");
        clickBook(66 + 20, 200, 0);
        pause(5);
        act(mc -> Report.check("Retry opens the viewfinder again", mc.screen == null && Viewfinder.isOpen()));
        clickInWorld();
        until(mc -> mc.screen instanceof JournalScreen s && s.sketchReady());
        pause(25);
        check("Retry keeps the title", s -> s.titleText().equals("The first house"));
        press(GLFW.GLFW_KEY_ENTER);
        type("Built with a good view of the road. The thatch is new, and the door sticks when it rains.");
        pause(3);
        shot("sketch_with_note");

        // Done keeps it, on the server, as a drawing
        clickBook(220, 200, 0);
        pause(15);
        check("Done keeps the sketch", s -> s.journal().entries.size() == 1 && s.journal().entries.get(0).isSketch() && s.journal().entries.get(0).map != null);
        check("the words are kept", s -> s.journal().entries.get(0).text.startsWith("Built with"));
        hover(-40, -6);
        shot("sketch_page");
        clickTab(Section.DRAWINGS);
        check("the D tab goes to it", s -> s.spread() == s.pageOfEntry(s.journal().entries.get(0).id) / 2);
        act(mc -> {
            String id = screen(mc).journal().id;
            Entry e = screen(mc).journal().entries.get(0);
            Path img = JournalStore.folder(mc.getSingleplayerServer(), id).resolve("img").resolve(e.map + ".bin");
            Report.check("the server stored the picture", Files.exists(img));
            try {
                Report.line("the sketch is " + Files.size(img) + " bytes");
            } catch (IOException ex) {
                Report.check("the picture can be read, it threw " + ex, false);
            }
        });

        // the server does not take a bad sketch
        act(mc -> {
            JournalScreen s = screen(mc);
            EJNetClient.send(EJPayloads.SAVE_SKETCH, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "bad", "", new byte[]{1, 2, 3}));
            EJNetClient.send(EJPayloads.SAVE_SKETCH, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), "../../evil", "bad", "", SketchDrawer.pack(new byte[SketchDrawer.W * SketchDrawer.H])));
            EJNetClient.send(EJPayloads.SAVE_SKETCH, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "big", "", new byte[9000]));
        });
        pause(10);
        check("a broken or forged sketch is refused", s -> s.journal().entries.size() == 1);

        // writing around a pasted thing still works on a sketch page, and an empty title gets a name in the list
        closeScreen();
        pause(5);
        openJournal();
        clickTab(Section.CONTENTS);
        shot("sketch_contents");

        // delete takes it and its file away
        clickTab(Section.DRAWINGS);
        clickTitleOf(s -> s.journal().entries.get(0).id);
        check("clicking the title of a kept sketch writes in the title", s -> s.focusName().equals("title"));
        pressWith(GLFW.GLFW_KEY_A, false, true);
        press(GLFW.GLFW_KEY_BACKSPACE);
        press(GLFW.GLFW_KEY_TAB);
        pressWith(GLFW.GLFW_KEY_A, false, true);
        press(GLFW.GLFW_KEY_BACKSPACE);
        press(esc);
        pause(10);
        check("a sketch with no words stays", s -> s.journal().entries.size() == 1);
        clickTitleOf(s -> s.journal().entries.get(0).id);
        clickBook(268, 200, 0);
        clickBook(268, 200, 0);
        pause(10);
        check("Delete takes a sketch away", s -> s.journal().entries.isEmpty());
        act(mc -> {
            Path img = JournalStore.folder(mc.getSingleplayerServer(), screen(mc).journal().id).resolve("img");
            try (Stream<Path> files = Files.list(img)) {
                Report.check("its picture file is gone", files.findAny().isEmpty());
            } catch (IOException e) {
                Report.check("its picture file is gone, it threw " + e, false);
            }
        });

        // a new sketch left unkept is thrown away
        clickChoice(1);
        pause(5);
        clickInWorld();
        press(esc);
        pause(10);
        check("Esc while it is sketching keeps nothing, or keeps it if it is done", s -> s.journal().entries.size() <= 1);
        closeScreen();
        finish();
    }

    /** Each mob stands still in front of the same view. The script saves the sketch and a real picture side by side. */
    private void sketchMob(String name, String summon, int distance, float facing) {
        onGround(-975, 188 - distance, summon, facing);
        pause(30);
        act(mc -> {
            Report.line("mobs in view for " + name + ": " + mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(40), e -> e != mc.player).size());
            Screenshot.grab(mc.gameDirectory, "mob_" + name + ".png", mc.getMainRenderTarget(), message -> {});
            sheetJob = new SketchJob(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), 62, 3);
        });
        until(mc -> {
            sheetJob.work(5_000_000);
            return sheetJob.done();
        });
        act(mc -> {
            try {
                Files.createDirectories(mc.gameDirectory.toPath().resolve("sketches"));
                Files.write(mc.gameDirectory.toPath().resolve("sketches/mob_" + name + ".lv"), sheetJob.image());
                Report.line("sketched " + name);
                if (name.equals("pig_close")) {
                    boolean eyes = false;
                    for (boolean dot : sheetJob.rays().dot) eyes |= dot;
                    Report.check("a pig up close has eyes", eyes);
                }
            } catch (IOException e) {
                Report.check("the sketch could be saved, it threw " + e, false);
            }
        });
        clearMobs();
    }

    /** Summons a mob that will not move, on the real ground at this spot. */
    private void onGround(int x, int z, String summon, float facing) {
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            String name = mc.player.getGameProfile().getName();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayerByName(name);
                int y = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                        String.format(java.util.Locale.ROOT, "summon %s %d %d %d {NoAI:1b,Rotation:[%.1ff,0f],%s}", summon.split(" ")[0], x, y, z, facing, summon.contains(" ") ? summon.substring(summon.indexOf(' ') + 1) : "Silent:1b"));
            });
        });
        pause(5);
    }

    /** Waits for the death smoke to clear, then takes away what the mob dropped. */
    private void clearMobs() {
        command("kill @e[type=!minecraft:player]");
        pause(65);
        command("kill @e[type=!minecraft:player]");
        pause(5);
    }

    /** More mobs than a sketch reads, all in view: the worst case for lag. */
    private void sketchCrowd() {
        String[] kinds = {"minecraft:sheep", "minecraft:pig", "minecraft:cow", "minecraft:villager", "minecraft:zombie", "minecraft:horse", "minecraft:chicken", "minecraft:wolf"};
        for (int i = 0; i < 80; i++) onGround(-985 + i % 10 * 2, 182 - i / 10 * 2, kinds[i % kinds.length], i * 40);
        pause(30);
        sketchHere();
        act(mc -> {
            Report.line(String.format(java.util.Locale.ROOT, "a crowd of mobs: slowest moment %.1f ms", sheetJob.longestMillis()));
            Report.check("a crowd of mobs causes no lag", sheetJob.longestMillis() < 16f);
            try {
                Files.write(mc.gameDirectory.toPath().resolve("sketches/mob_crowd.lv"), sheetJob.image());
            } catch (IOException e) {
                Report.check("the sketch could be saved, it threw " + e, false);
            }
            Screenshot.grab(mc.gameDirectory, "mob_crowd.png", mc.getMainRenderTarget(), message -> {});
        });
        clearMobs();
    }

    // ---- drawing by hand

    private void downOn(int x, int y, int button) {
        act(mc -> mc.screen.mouseClicked(screen(mc).canvasCellX(x), screen(mc).canvasCellY(y), button));
        pause(1);
    }

    private void dragOn(int x, int y, int button) {
        act(mc -> mc.screen.mouseDragged(screen(mc).canvasCellX(x), screen(mc).canvasCellY(y), button, 0, 0));
        pause(1);
    }

    private void upOn(int x, int y, int button) {
        act(mc -> mc.screen.mouseReleased(screen(mc).canvasCellX(x), screen(mc).canvasCellY(y), button));
        pause(1);
    }

    private void strokeOn(int x0, int y0, int x1, int y1, int button) {
        downOn(x0, y0, button);
        dragOn(x1, y1, button);
        upOn(x1, y1, button);
    }

    private void pickTool(int i) {
        act(mc -> mc.screen.mouseClicked(DrawingDesk.toolX(screen(mc).menuX(), i) + 5, DrawingDesk.toolY(screen(mc).menuTop()) + 5, 0));
        pause(2);
    }

    private void pickInk(DyeColor color) {
        act(mc -> {
            int i = DrawingDesk.inkMenuIndex(color);
            mc.screen.mouseClicked(DrawingDesk.inkX(screen(mc).menuX(), i) + 5, DrawingDesk.inkY(screen(mc).menuTop(), i) + 5, 0);
        });
        pause(2);
    }

    private static int inkAt(JournalScreen s, int x, int y) {
        return s.desk().canvas().at(x, y);
    }

    private static int inkOf(DyeColor color) {
        return color.getId() + 1;
    }

    private static int inkCount(JournalScreen s) {
        int count = 0;
        for (byte b : s.desk().canvas().pixels()) {
            if (b != 0) count++;
        }
        return count;
    }

    private void clickDone() {
        clickBook(220, 200, 0);
    }

    /** Wipes the page with the right button, then draws a small camp with every tool, for the picture in the plan. */
    private void drawCampPicture() {
        for (int y = 0; y < PixelCanvas.SIZE; y++) strokeOn(0, y, 63, y, 1);
        pickTool(3);
        pickInk(DyeColor.BROWN);
        strokeOn(14, 46, 32, 18, 0);
        strokeOn(32, 18, 50, 46, 0);
        strokeOn(14, 46, 50, 46, 0);
        pickTool(2);
        strokeOn(32, 34, 32, 34, 0);
        pickTool(3);
        pickInk(DyeColor.BLACK);
        strokeOn(28, 46, 32, 36, 0);
        strokeOn(32, 36, 36, 46, 0);
        strokeOn(28, 46, 36, 46, 0);
        pickTool(2);
        strokeOn(32, 43, 32, 43, 0);
        pickTool(3);
        pickInk(DyeColor.GREEN);
        strokeOn(0, 47, 63, 47, 0);
        strokeOn(6, 47, 2, 40, 0);
        strokeOn(2, 40, 6, 32, 0);
        strokeOn(6, 32, 10, 40, 0);
        strokeOn(10, 40, 6, 47, 0);
        pickTool(2);
        strokeOn(6, 40, 6, 40, 0);
        pickTool(0);
        pickInk(DyeColor.YELLOW);
        for (int y = 5; y < 12; y++) strokeOn(50, y, 56, y, 0);
        pickInk(DyeColor.ORANGE);
        strokeOn(56, 46, 56, 41, 0);
        strokeOn(58, 46, 58, 43, 0);
        pickInk(DyeColor.BLACK);
    }

    private void drawing() {
        inWorld();
        command("gamemode creative");
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("give @a minecraft:poppy 2");
        pause(20);
        openJournal();
        pause(10);

        // the inks are vanilla's dye colours
        act(mc -> {
            boolean same = true;
            for (DyeColor color : DyeColor.values()) same &= PixelCanvas.INK[color.getId() + 1] == dyeColour(color);
            Report.check("each ink is its vanilla dye colour", same);
        });

        // a new drawing: blank, with the tools across from it
        clickChoice(drawRow());
        pause(5);
        check("Draw something opens a blank canvas", s -> s.desk() != null && s.desk().canvas().isBlank());
        check("the title is ready for typing", s -> s.focusName().equals("title"));
        hover(-40, -6);
        shot("drawing_open");

        // a stroke
        strokeOn(10, 10, 30, 10, 0);
        check("a click on the canvas leaves the title", s -> s.focusName().isEmpty());
        check("a fast drag leaves no gaps", s -> {
            boolean all = true;
            for (int x = 10; x <= 30; x++) all &= inkAt(s, x, 10) == inkOf(DyeColor.BLACK);
            return all && inkCount(s) == 21;
        });
        strokeOn(5, 5, 20, 40, 0);
        check("a slanted drag leaves no gaps", s -> inkCount(s) > 21 + 30 && inkAt(s, 5, 5) != 0 && inkAt(s, 20, 40) != 0);

        // undo and redo, with the real keys and with the buttons
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("Control and Z takes back one whole stroke", s -> inkCount(s) == 21);
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("Control and Z again takes back the first", s -> inkCount(s) == 0);
        pressWith(GLFW.GLFW_KEY_Y, false, true);
        check("Control and Y brings it back", s -> inkCount(s) == 21);
        pressWith(GLFW.GLFW_KEY_Z, true, true);
        check("Control, Shift, and Z brings back the next", s -> inkCount(s) > 21 + 30);
        pickTool(4);
        check("the Undo button works", s -> inkCount(s) == 21);
        pickTool(5);
        check("the Redo button works", s -> inkCount(s) > 21 + 30);
        pickTool(4);
        strokeOn(1, 60, 2, 60, 0);
        check("a new stroke clears what Redo could bring back", s -> !s.desk().canvas().canRedo());
        pickTool(4);

        // inks
        pickInk(DyeColor.RED);
        act(mc -> Report.check("picking an ink chooses it", DrawingDesk.ink() == inkOf(DyeColor.RED)));
        strokeOn(10, 12, 12, 12, 0);
        check("it draws in that ink", s -> inkAt(s, 11, 12) == inkOf(DyeColor.RED));

        // the paper erases, and so does a right drag with any tool
        pickTool(1);
        act(mc -> Report.check("the paper is chosen", DrawingDesk.tool() == DrawingDesk.Tool.PAPER));
        strokeOn(10, 10, 12, 10, 0);
        check("the paper erases", s -> inkAt(s, 11, 10) == 0 && inkAt(s, 13, 10) != 0);
        pickInk(DyeColor.BLACK);
        act(mc -> Report.check("picking an ink while erasing goes back to the feather", DrawingDesk.tool() == DrawingDesk.Tool.FEATHER));
        strokeOn(13, 10, 15, 10, 1);
        check("a right drag erases with the feather", s -> inkAt(s, 14, 10) == 0 && inkAt(s, 16, 10) != 0);

        // a line stays a preview until you let go, and the mouse can run off the canvas
        pickTool(3);
        downOn(40, 40, 0);
        dragOn(60, 50, 0);
        check("a straight line changes nothing until you let go", s -> inkAt(s, 60, 50) == 0 && inkAt(s, 40, 40) == 0);
        shot("drawing_line_preview");
        upOn(60, 50, 0);
        check("letting go draws the line", s -> inkAt(s, 60, 50) != 0 && inkAt(s, 40, 40) != 0 && inkAt(s, 50, 45) != 0);
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("one undo takes the whole line", s -> inkAt(s, 60, 50) == 0 && inkAt(s, 50, 45) == 0);
        pickTool(0);
        act(mc -> {
            int x = screen(mc).canvasCellX(62), y = screen(mc).canvasCellY(20);
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseDragged(x + 200, y, 0, 0, 0);
            mc.screen.mouseReleased(x + 200, y, 0);
        });
        pause(2);
        check("a stroke that runs off the canvas stops at its edge", s -> inkAt(s, 63, 20) != 0);
        pressWith(GLFW.GLFW_KEY_Z, false, true);

        // fill stays inside a closed shape
        pickTool(3);
        strokeOn(40, 20, 50, 20, 0);
        strokeOn(50, 20, 50, 30, 0);
        strokeOn(50, 30, 40, 30, 0);
        strokeOn(40, 30, 40, 20, 0);
        pickInk(DyeColor.BLUE);
        pickTool(2);
        strokeOn(45, 25, 45, 25, 0);
        check("the bucket fills inside the shape", s -> inkAt(s, 45, 25) == inkOf(DyeColor.BLUE) && inkAt(s, 41, 21) == inkOf(DyeColor.BLUE));
        check("the bucket stops at its edge", s -> inkAt(s, 40, 20) == inkOf(DyeColor.BLACK) && inkAt(s, 39, 25) == 0 && inkAt(s, 51, 25) == 0);
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("one undo takes the whole fill", s -> inkAt(s, 45, 25) == 0);
        pickTool(0);
        pickInk(DyeColor.BLACK);

        // a hover shows what each thing is
        act(mc -> {
            int x = DrawingDesk.toolX(screen(mc).menuX(), 2) + 5, y = DrawingDesk.toolY(screen(mc).menuTop()) + 5;
            hover0(mc, x - bookX(mc), y - bookY(mc));
        });
        pause(3);
        shot("drawing_tool_hint");
        hover(-40, -6);

        // words, and keys that go to the words
        clickTitleOf(JournalScreen::editingId);
        check("clicking the title writes in the title", s -> s.focusName().equals("title"));
        type("Camp");
        check("typing goes in the title", s -> s.titleText().equals("Camp"));
        final int[] before = new int[1];
        act(mc -> before[0] = inkCount(screen(mc)));
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("in the title, Control and Z undoes the words, not the drawing", s -> !s.titleText().equals("Camp") && inkCount(s) == before[0]);
        pressWith(GLFW.GLFW_KEY_Y, false, true);
        check("and Control and Y brings the words back", s -> s.titleText().equals("Camp"));
        strokeOn(2, 2, 3, 2, 0);
        type("xyz");
        check("with no writing chosen, typing does nothing", s -> s.titleText().equals("Camp") && s.bodyText().isEmpty());
        check("a stray key does not close the book", s -> s.desk() != null);

        // a long note runs on, and the tools step aside for it
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = s.menuX() == bookX(mc) + 166 ? bookX(mc) + 17 + 30 : bookX(mc) + 166 + 30;
            mc.screen.mouseClicked(x, s.menuTop() + 145, 0);
            mc.screen.mouseReleased(x, s.menuTop() + 145, 0);
        });
        pause(3);
        check("a click below the drawing writes the note", s -> s.focusName().equals("body"));
        type("A small tent under a yellow sun, with the grass all around it, and more words to run onto the next page.");
        check("the note goes on past the first page", s -> s.caretPage() != s.editingPage());
        hover(-40, -6);
        shot("drawing_note_runs_on");
        strokeOn(4, 4, 5, 4, 0);
        check("a click on the canvas brings the tools back", s -> s.focusName().isEmpty() && inkAt(s, 4, 4) != 0);

        // pasting works too, and a thing taped on the drawing is not drawn on
        act(mc -> mc.screen.mouseClicked(bookX(mc) + 66 + 20, bookY(mc) + 200, 0));
        pause(4);
        check("Paste opens the picker over the tools", JournalScreen::pickerOpen);
        press(GLFW.GLFW_KEY_ESCAPE);
        check("Esc goes back to the tools", s -> !s.pickerOpen() && s.desk() != null);
        act(mc -> mc.screen.mouseClicked(bookX(mc) + 66 + 20, bookY(mc) + 200, 0));
        pause(4);
        act(mc -> mc.screen.mouseClicked(screen(mc).pickerCellX(0) + 5, screen(mc).pickerCellY(0) + 5, 0));
        pause(4);
        act(mc -> mc.screen.mouseClicked(screen(mc).canvasCellX(20), screen(mc).canvasCellY(50), 0));
        pause(15);
        check("a thing can be taped on a new drawing", s -> s.keepsakesOnWriting() == 1 && s.desk() != null && s.journal().entries.size() == 1 && s.journal().entries.get(0).isDrawing());
        final int[] count = new int[1];
        act(mc -> count[0] = inkCount(screen(mc)));
        downOn(20, 50, 0);
        upOn(20, 50, 0);
        check("a click on the thing does not draw under it", s -> inkCount(s) == count[0]);
        downOn(20, 50, 1);
        upOn(20, 50, 1);
        pause(15);
        check("a right-click peels it off", s -> s.keepsakesOnWriting() == 0);

        // a picture for the plan
        pressWith(GLFW.GLFW_KEY_ESCAPE, false, false);
        pause(10);
        check("Esc keeps the drawing, and the book stays open", s -> s.desk() == null && s.journal().entries.size() == 1 && s.journal().entries.get(0).map != null);
        act(mc -> Report.check("the book is still open", mc.screen instanceof JournalScreen));

        // kept: it is in the list, and on the server
        final String[] image = new String[1];
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry e = s.journal().entries.get(0);
            image[0] = e.map;
            Path dir = JournalStore.folder(mc.getSingleplayerServer(), s.journal().id).resolve("img");
            Report.check("the server stored the picture", Files.exists(dir.resolve(e.map + ".bin")));
            try {
                Report.line("this drawing is " + Files.size(dir.resolve(e.map + ".bin")) + " bytes");
            } catch (IOException ex) {
                Report.check("the picture can be read, it threw " + ex, false);
            }
        });
        clickTab(Section.CONTENTS);
        shot("drawing_contents");
        clickTab(Section.DRAWINGS);
        check("the D tab goes to it", s -> s.spread() == s.pageOfEntry(s.journal().entries.get(0).id) / 2);
        hover(-40, -6);
        pause(5);
        shot("drawing_reading");

        // open it again by clicking the picture
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(0).id);
            int x = bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, y = bookY(mc) + 16 + 12 + 60;
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
        });
        pause(5);
        check("clicking the picture opens it for drawing", s -> s.desk() != null && s.focusName().isEmpty() && !s.desk().canvas().changed() && !s.desk().canvas().isBlank());
        check("it comes back the same", s -> java.util.Arrays.equals(s.desk().canvas().pixels(), MapImages.drawingPixels(s.journal().entries.get(0).map)));
        drawCampPicture();
        hover(-40, -6);
        pause(3);
        shot("drawing_tools");
        clickDone();
        pause(10);
        check("Done keeps it", s -> s.desk() == null);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry e = s.journal().entries.get(0);
            Report.check("a changed picture gets a new file", !e.map.equals(image[0]));
            Path dir = JournalStore.folder(mc.getSingleplayerServer(), s.journal().id).resolve("img");
            Report.check("the old file is gone", !Files.exists(dir.resolve(image[0] + ".bin")) && Files.exists(dir.resolve(e.map + ".bin")));
            image[0] = e.map;
        });
        hover(-40, -6);
        pause(5);
        shot("drawing_page");

        // words alone do not make a new picture
        clickTitleOf(s -> s.journal().entries.get(0).id);
        check("clicking the title of a kept drawing writes in the title", s -> s.focusName().equals("title") && s.desk() != null);
        press(GLFW.GLFW_KEY_END);
        type(" one");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("the new title is kept", s -> s.journal().entries.get(0).title.equals("Camp one"));
        check("words alone keep the same picture", s -> s.journal().entries.get(0).map.equals(image[0]));

        // the server does not take a bad drawing
        act(mc -> {
            JournalScreen s = screen(mc);
            byte[] tooHigh = new byte[PixelCanvas.SIZE * PixelCanvas.SIZE];
            tooHigh[5] = 17;
            byte[] tooShort = new byte[100];
            String note = UUID.randomUUID().toString();
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "bad", "", new byte[]{1, 2, 3}));
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "big", "", new byte[9000]));
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "ink", "", new PixelCanvas(tooHigh).pack()));
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "short", "", new PixelCanvas(tooShort).pack()));
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, UUID.randomUUID().toString(), "../../evil", "path", "", new PixelCanvas(null).pack()));
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, "not-a-uuid", UUID.randomUUID().toString(), "id", "", new PixelCanvas(null).pack()));
        });
        pause(10);
        check("a broken or forged drawing is refused", s -> s.journal().entries.size() == 1 && s.journal().entries.get(0).map.equals(image[0]));
        act(mc -> {
            Path dir = JournalStore.folder(mc.getSingleplayerServer(), screen(mc).journal().id).resolve("img");
            try (Stream<Path> files = Files.list(dir)) {
                Report.check("and leaves no file", files.count() == 1);
            } catch (IOException e) {
                Report.check("and leaves no file, it threw " + e, false);
            }
        });

        // the worst picture there is still fits
        final String[] noisy = new String[1];
        act(mc -> {
            byte[] noise = new byte[PixelCanvas.SIZE * PixelCanvas.SIZE];
            java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < noise.length; i++) noise[i] = (byte) random.nextInt(PixelCanvas.INK.length);
            byte[] packed = new PixelCanvas(noise).pack();
            Report.line("the noisiest drawing is " + packed.length + " bytes");
            noisy[0] = UUID.randomUUID().toString();
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(screen(mc).journal().id, noisy[0], UUID.randomUUID().toString(), "Noise", "", packed));
        });
        pause(15);
        check("the noisiest possible drawing is kept", s -> s.journal().entry(noisy[0]) != null);

        // a drawing that starts on a right page has its tools on the left
        act(mc -> Report.line("blank page is " + screen(mc).blankPage()));
        act(mc -> {
            JournalScreen s = screen(mc);
            EJNetClient.send(EJPayloads.DELETE_ENTRY, new EJPayloads.DeleteEntry(s.journal().id, noisy[0]));
        });
        pause(10);
        clickChoice(drawRow());
        pause(5);
        check("the tools sit across from the canvas", s -> (s.editingPage() % 2 == 0) == (s.menuX() > s.canvasCellX(0)));
        strokeOn(30, 30, 40, 30, 0);
        check("drawing works on this side of the book", s -> inkAt(s, 35, 30) != 0);
        act(mc -> Report.line("this drawing starts on page " + screen(mc).editingPage()));
        pickTool(4);
        check("and with nothing drawn or written", s -> s.desk().canvas().isBlank());
        clickDone();
        pause(10);
        check("a blank drawing with no words is not kept", s -> s.journal().entries.size() == 1);

        // Delete asks first, and takes the picture too
        clickChoice(drawRow());
        pause(5);
        strokeOn(30, 30, 40, 30, 0);
        clickDone();
        pause(10);
        check("the second drawing is kept", s -> s.journal().entries.size() == 2);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(1).id);
            int x = bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, y = bookY(mc) + 16 + 12 + 60;
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
        });
        pause(5);
        clickBook(268, 200, 0);
        check("Delete asks first", JournalScreen::deleteArmed);
        clickBook(268, 200, 0);
        pause(10);
        check("Delete takes the drawing away", s -> s.journal().entries.size() == 1 && s.desk() == null);
        act(mc -> {
            Path dir = JournalStore.folder(mc.getSingleplayerServer(), screen(mc).journal().id).resolve("img");
            try (Stream<Path> files = Files.list(dir)) {
                Report.check("and its picture file", files.count() == 1);
            } catch (IOException e) {
                Report.check("and its picture file, it threw " + e, false);
            }
        });

        // erase it all and take the words away, and it is gone like an emptied note
        clickChoice(drawRow());
        pause(5);
        strokeOn(30, 30, 40, 30, 0);
        clickDone();
        pause(10);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(1).id);
            int x = bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, y = bookY(mc) + 16 + 12 + 60;
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
        });
        pause(5);
        strokeOn(30, 30, 40, 30, 1);
        check("all erased", s -> s.desk().canvas().isBlank());
        clickDone();
        pause(10);
        check("a drawing with nothing left is gone", s -> s.journal().entries.size() == 1);

        // a click outside keeps your work and leaves the book open
        clickTab(Section.DRAWINGS);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(0).id);
            int x = bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, y = bookY(mc) + 16 + 12 + 100;
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
        });
        pause(5);
        check("clicking the picture opens it again", s -> s.desk() != null);
        strokeOn(62, 62, 63, 62, 0);
        act(mc -> mc.screen.mouseClicked(2, 2, 0));
        pause(10);
        act(mc -> Report.check("a click outside stops drawing and leaves the book open", mc.screen instanceof JournalScreen && screen(mc).desk() == null));
        check("and it keeps the change", s -> MapImages.drawingPixels(s.journal().entries.get(0).map)[62 * PixelCanvas.SIZE + 63] != 0);

        // and closing the book while drawing keeps it too
        closeScreen();
        pause(5);
        openJournal();
        pause(10);
        clickTab(Section.DRAWINGS);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(0).id);
            s.mouseClicked(bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, bookY(mc) + 16 + 12 + 100, 0);
            s.mouseReleased(bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, bookY(mc) + 16 + 12 + 100, 0);
        });
        pause(5);
        check("the picture opens after closing the book", s -> s.desk() != null);
        strokeOn(60, 60, 61, 60, 0);
        closeScreen();
        pause(10);
        openJournal();
        pause(15);
        check("closing the book keeps it", s -> {
            byte[] pixels = MapImages.drawingPixels(s.journal().entries.get(0).map);
            return pixels != null && pixels[60 * PixelCanvas.SIZE + 60] != 0;
        });
        closeScreen();
        finish();
    }

    /** A new run: the client has no pictures yet, so this one comes from the server's file. */
    private void drawingAgain() {
        inWorld();
        openJournal();
        pause(10);
        check("the drawing is still in the journal", s -> s.journal().entries.size() == 1 && s.journal().entries.get(0).isDrawing() && s.journal().entries.get(0).title.equals("Camp one"));
        clickTab(Section.DRAWINGS);
        pause(40);
        check("its picture comes from the server", s -> {
            byte[] pixels = MapImages.drawingPixels(s.journal().entries.get(0).map);
            return pixels != null && pixels[60 * PixelCanvas.SIZE + 60] != 0 && pixels[62 * PixelCanvas.SIZE + 63] != 0;
        });
        hover(-40, -6);
        shot("drawing_after_restart");
        closeScreen();
        finish();
    }

    private void sketchHere() {
        act(mc -> sheetJob = new SketchJob(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), 62, 3));
        until(mc -> {
            sheetJob.work(5_000_000);
            return sheetJob.done();
        });
    }

    /** More mobs than the limit stand close behind you. The golem ahead is farther away than all of them. */
    private void sketchBehind() {
        final byte[][] empty = new byte[1][];
        sketchHere();
        act(mc -> empty[0] = sheetJob.image());
        for (int i = 0; i < 70; i++) onGround(-980 + i % 10, 190 + i / 10, "minecraft:pig", 0);
        onGround(-975, 176, "minecraft:iron_golem", 0);
        pause(30);
        sketchHere();
        act(mc -> Report.check("mobs behind you do not push a mob ahead out of the sketch", !Arrays.equals(sheetJob.image(), empty[0])));
        clearMobs();
    }

    // ---- sketches of many kinds of place, beside real pictures

    private record PlaceSpot(ServerLevel level, double x, double eyeY, double z, float yaw, float pitch) {}

    private volatile boolean placeReady, placeFound;

    /** Goes there, then saves a real picture, the sketch, and what the rays saw, to tune the lines with. */
    private void sketchPlace(String name, java.util.function.Function<MinecraftServer, PlaceSpot> find) {
        act(mc -> {
            placeReady = false;
            MinecraftServer server = mc.getSingleplayerServer();
            String who = mc.player.getGameProfile().getName();
            server.execute(() -> {
                PlaceSpot spot = null;
                try {
                    spot = find.apply(server);
                } catch (RuntimeException e) {
                    Report.line("finding " + name + " threw " + e);
                }
                placeFound = spot != null;
                if (spot != null) {
                    server.getPlayerList().getPlayerByName(who).teleportTo(spot.level(), spot.x(), spot.eyeY() - 1.62, spot.z(), spot.yaw(), spot.pitch());
                } else {
                    Report.line("no place found for " + name);
                }
                placeReady = true;
            });
        });
        until(mc -> placeReady);
        pause(240);
        act(mc -> {
            sheetJob = null;
            if (!placeFound) return;
            Screenshot.grab(mc.gameDirectory, "place_" + name + ".png", mc.getMainRenderTarget(), message -> {});
            sheetJob = new SketchJob(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), 62, 3);
        });
        until(mc -> {
            if (sheetJob == null) return true;
            sheetJob.work(5_000_000);
            return sheetJob.done();
        });
        act(mc -> {
            if (sheetJob == null) return;
            try {
                Path dir = mc.gameDirectory.toPath().resolve("sketches");
                Files.createDirectories(dir);
                saveSheetJob(dir, name);
            } catch (IOException e) {
                Report.check("the sketch could be saved, it threw " + e, false);
            }
        });
    }

    /** The sketch, and what the rays saw, to tune the lines with. */
    private void saveSheetJob(Path dir, String name) throws IOException {
        Files.write(dir.resolve("place_" + name + ".lv"), sheetJob.image());
        SketchRays r = sheetJob.rays();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(dir.resolve("place_" + name + ".rays"))))) {
            for (Object array : new Object[]{r.t, r.family, r.normal, r.ground, r.blockHash, r.blockY, r.u, r.v, r.upper, r.plantT, r.plantFlower, r.part, r.dot, r.cloud}) out.writeObject(array);
            out.writeObject(new float[]{r.sunX, r.sunY, r.moonX, r.moonY});
            out.writeObject(new int[]{r.moonPhase, r.night ? 1 : 0, r.rain ? 1 : 0, r.snow ? 1 : 0});
        }
        Report.line("sketched " + name + ": " + SketchDrawer.pack(sheetJob.image()).length + " bytes, slowest moment " + sheetJob.longestMillis() + " ms");
    }

    private static float widestView(ServerLevel level, double x, double eyeY, double z) {
        float best = 0;
        double far = -1;
        Vec3 from = new Vec3(x, eyeY, z);
        for (int i = 0; i < 8; i++) {
            float yaw = i * 45f;
            BlockHitResult hit = level.clip(ray(level, from, from.add(Vec3.directionFromRotation(0f, yaw).scale(80))));
            double d = hit.getLocation().distanceTo(from);
            if (d > far) {
                far = d;
                best = yaw;
            }
        }
        return best;
    }

    /** Standing on the real ground, or the water, looking the widest way. */
    private static PlaceSpot onSurface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        double eyeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1.62;
        return new PlaceSpot(level, x + .5, eyeY, z + .5, widestView(level, x + .5, eyeY, z + .5), 4f);
    }

    /** The first spot from here down with air to stand in and a floor. */
    private static int floorBelow(ServerLevel level, int x, int top, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top; y > level.getMinBuildHeight() + 1; y--) {
            boolean air = level.getBlockState(p.set(x, y, z)).isAir() && level.getBlockState(p.set(x, y + 1, z)).isAir() && level.getBlockState(p.set(x, y + 2, z)).isAir();
            if (air && level.getBlockState(p.set(x, y - 1, z)).isSolid()) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Stands in the most open spot near here that has a floor. For caves and the Nether. */
    private static PlaceSpot inOpenSpace(ServerLevel level, int cx, int cy, int cz) {
        PlaceSpot best = null;
        double far = -1;
        for (int dx = -24; dx <= 24; dx += 12) {
            for (int dz = -24; dz <= 24; dz += 12) {
                int y = floorBelow(level, cx + dx, cy, cz + dz);
                if (y == Integer.MIN_VALUE) continue;
                double eyeY = y + 1.62, x = cx + dx + .5, z = cz + dz + .5;
                float yaw = widestView(level, x, eyeY, z);
                Vec3 from = new Vec3(x, eyeY, z);
                double d = level.clip(ray(level, from, from.add(Vec3.directionFromRotation(0f, yaw).scale(80)))).getLocation().distanceTo(from);
                if (d > far) {
                    far = d;
                    best = new PlaceSpot(level, x, eyeY, z, yaw, 4f);
                }
            }
        }
        return best;
    }

    private static BlockPos biome(ServerLevel level, String id, BlockPos near) {
        var found = level.findClosestBiome3d(h -> h.is(id(id)), near, 12800, 32, 64);
        return found == null ? null : found.getFirst();
    }

    private static BlockPos structure(ServerLevel level, String id, BlockPos near) {
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, id(id)));
        if (holder.isEmpty()) return null;
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), near, 200, false);
        return found == null ? null : found.getFirst();
    }

    private static PlaceSpot lookingAt(ServerLevel level, BlockPos center, int back) {
        int x = center.getX() - back, z = center.getZ();
        level.getChunk(x >> 4, z >> 4);
        double eyeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2.62;
        return new PlaceSpot(level, x + .5, eyeY, z + .5, -90f, 6f);
    }

    private void surfaceBiome(String name) {
        sketchPlace(name, server -> {
            BlockPos at = biome(server.overworld(), "minecraft:" + name, new BlockPos(-975, 64, 188));
            return at == null ? null : onSurface(server.overworld(), at.getX(), at.getZ());
        });
    }

    private void caveBiome(String name) {
        sketchPlace(name, server -> {
            BlockPos at = biome(server.overworld(), "minecraft:" + name, new BlockPos(-975, 20, 188));
            return at == null ? null : inOpenSpace(server.overworld(), at.getX(), at.getY() + 10, at.getZ());
        });
    }

    private void overworldStructure(String name, int back) {
        sketchPlace(name, server -> {
            BlockPos at = structure(server.overworld(), "minecraft:" + name, new BlockPos(-975, 64, 188));
            return at == null ? null : lookingAt(server.overworld(), at, back);
        });
    }

    private void netherBiome(String name) {
        sketchPlace(name, server -> {
            ServerLevel nether = server.getLevel(Level.NETHER);
            BlockPos at = biome(nether, "minecraft:" + name, new BlockPos(0, 64, 0));
            return at == null ? null : inOpenSpace(nether, at.getX(), 100, at.getZ());
        });
    }

    private void netherStructure(String name) {
        sketchPlace(name, server -> {
            ServerLevel nether = server.getLevel(Level.NETHER);
            BlockPos at = structure(nether, "minecraft:" + name, new BlockPos(0, 64, 0));
            return at == null ? null : inOpenSpace(nether, at.getX(), 100, at.getZ());
        });
    }

    private void sketchPlaces() {
        inWorld();
        command("gamemode spectator");
        command("time set noon");
        command("weather clear");
        command("gamerule doDaylightCycle false");
        command("effect give @s minecraft:night_vision infinite 0 true");
        act(mc -> mc.options.hideGui = true);
        pause(20);
        for (String name : new String[]{"desert", "snowy_plains", "ice_spikes", "jungle", "bamboo_jungle", "dark_forest", "swamp", "mangrove_swamp", "badlands", "cherry_grove", "savanna", "taiga", "meadow", "stony_peaks", "flower_forest", "mushroom_fields", "warm_ocean", "beach"}) surfaceBiome(name);
        for (String name : new String[]{"lush_caves", "dripstone_caves", "deep_dark"}) caveBiome(name);
        overworldStructure("village_desert", 25);
        overworldStructure("village_snowy", 25);
        overworldStructure("village_savanna", 25);
        overworldStructure("village_taiga", 25);
        overworldStructure("pillager_outpost", 22);
        overworldStructure("swamp_hut", 12);
        overworldStructure("desert_pyramid", 22);
        overworldStructure("jungle_pyramid", 18);
        overworldStructure("ruined_portal", 12);
        for (String name : new String[]{"nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"}) netherBiome(name);
        netherStructure("fortress");
        netherStructure("bastion_remnant");
        sketchPlace("end_island", server -> onSurface(server.getLevel(Level.END), 60, 20));
        sketchPlace("end_city", server -> {
            ServerLevel end = server.getLevel(Level.END);
            BlockPos at = structure(end, "minecraft:end_city", new BlockPos(1000, 64, 0));
            return at == null ? null : lookingAt(end, at, 10);
        });
        act(mc -> mc.options.hideGui = false);
        command("gamerule doDaylightCycle true");
        finish();
    }

    private void sketchMobs() {
        inWorld();
        command("kill @e[type=!minecraft:player]");
        command("gamemode spectator");
        command("tp @s -975 70 188 180 0");
        command("time set noon");
        command("difficulty normal");
        pause(100);
        sketchMob("pig_close", "minecraft:pig", 3, 30);
        sketchMob("pig_far", "minecraft:pig", 8, 30);
        sketchMob("baby_pig", "minecraft:pig Age:-24000", 3, 30);
        // nearer than 3 or farther than 6: a step in the ground between hides the feet
        sketchMob("cow", "minecraft:cow", 6, 60);
        sketchMob("villager", "minecraft:villager", 6, 0);
        sketchMob("zombie", "minecraft:zombie", 6, 20);
        sketchMob("creeper", "minecraft:creeper", 6, 0);
        sketchMob("skeleton", "minecraft:skeleton", 6, 40);
        sketchMob("spider", "minecraft:spider", 6, 30);
        sketchMob("chicken", "minecraft:chicken", 3, 0);
        sketchMob("sheep", "minecraft:sheep", 3, 50);
        sketchMob("baby_sheep", "minecraft:sheep Age:-24000", 3, 50);
        sketchMob("horse", "minecraft:horse", 7, 60);
        sketchMob("wolf", "minecraft:wolf", 3, 30);
        sketchMob("iron_golem", "minecraft:iron_golem", 8, 0);
        sketchMob("rabbit", "minecraft:rabbit", 3, 40);
        sketchMob("llama", "minecraft:llama", 7, 40);
        sketchMob("armored", "minecraft:zombie ArmorItems:[{id:\"minecraft:iron_boots\",count:1},{id:\"minecraft:iron_leggings\",count:1},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1}],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]", 6, 20);
        sketchMob("slime", "minecraft:slime Size:3", 6, 20);
        sketchMob("boat", "minecraft:boat Silent:1b", 3, 30);
        sketchMob("minecart", "minecraft:minecart Silent:1b", 3, 30);
        sketchMob("armor_stand", "minecraft:armor_stand ShowArms:1b", 3, 20);
        sketchCrowd();
        sketchBehind();
        command("gamemode creative");
        finish();
    }

    /** The same street at different times, for the sun, the moon, and the weather. */
    private void sketchSky() {
        inWorld();
        command("gamemode spectator");
        command("effect give @s minecraft:night_vision infinite 0 true");
        command("gamerule doDaylightCycle false");
        act(mc -> mc.options.hideGui = true);
        pause(20);
        skyView("sky_noon", "6000", "clear", 180, -30);
        act(mc -> {
            boolean any = false;
            for (boolean cloud : sheetJob.rays().cloud) any |= cloud;
            Report.check("clouds show when you look up", any);
            Report.check("the sun is not in this view", Float.isNaN(sheetJob.rays().sunX) || sheetJob.rays().sunY < 0 || sheetJob.rays().sunY > SketchDrawer.H);
        });
        skyView("sky_sunset", "11300", "clear", 90, -8);
        act(mc -> Report.check("the low sun is in the frame, to the west", inFrame(sheetJob.rays().sunX, sheetJob.rays().sunY) && !sheetJob.rays().night));
        skyView("sky_night", "18000", "clear", 180, -35);
        act(mc -> Report.check("midnight is night", sheetJob.rays().night));
        skyView("sky_moon_low", "14000", "clear", -90, -15);
        act(mc -> Report.check("the rising moon is in the frame, to the east", inFrame(sheetJob.rays().moonX, sheetJob.rays().moonY) && sheetJob.rays().night));
        skyView("sky_rain", "6000", "rain", 180, 0);
        act(mc -> Report.check("rain is noted, and hides the sun", sheetJob.rays().rain && Float.isNaN(sheetJob.rays().sunX)));
        skyView("sky_street", "6000", "clear", 180, 0);
        act(mc -> Report.check("clear weather has no rain", !sheetJob.rays().rain && !sheetJob.rays().snow));
        act(mc -> mc.options.hideGui = false);
        command("time set noon");
        command("weather clear");
        command("gamerule doDaylightCycle true");
        finish();
    }

    private static boolean inFrame(float x, float y) {
        return x >= 0 && y >= 0 && x < SketchDrawer.W && y < SketchDrawer.H;
    }

    private void skyView(String name, String time, String weather, float yaw, float pitch) {
        command("time set " + time);
        command("weather " + weather);
        command(String.format(java.util.Locale.ROOT, "tp @s -975 70 188 %.1f %.1f", yaw, pitch));
        pause(weather.equals("rain") ? 240 : 100);
        act(mc -> {
            Screenshot.grab(mc.gameDirectory, "place_" + name + ".png", mc.getMainRenderTarget(), message -> {});
            sheetJob = new SketchJob(mc.level, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), 62, 3);
        });
        until(mc -> {
            sheetJob.work(5_000_000);
            return sheetJob.done();
        });
        act(mc -> {
            try {
                saveSheetJob(mc.gameDirectory.toPath().resolve("sketches"), name);
            } catch (IOException e) {
                Report.check("the sketch could be saved, it threw " + e, false);
            }
        });
    }

    private void sketchSheet() {
        inWorld();
        command("gamemode spectator");
        act(mc -> mc.options.hideGui = true);
        pause(20);
        sketchView("house", -996, 68.62, 122, -121, -6);
        sketchView("street", -975, 71.62, 188, 180, 0);
        sketchView("river", -950, 66.62, 104, 170, 6);
        sketchView("hill", -1020, 88, 170, -80, 10);
        sketchView("trees", -912, 69.62, 178, -90, 0);
        act(mc -> mc.options.hideGui = false);
        command("gamemode creative");
        finish();
    }

    // ---- pasting things in

    private static int inInventory(Minecraft mc, Item item) {
        int count = 0;
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) count += inventory.getItem(i).getCount();
        }
        return count;
    }

    private static int inJournal(JournalScreen s, Item item) {
        String id = "\"" + BuiltInRegistries.ITEM.getKey(item) + "\"";
        int count = 0;
        for (Entry entry : s.journal().entries) {
            for (Keepsake keepsake : entry.keepsakes) {
                if (keepsake.item.contains(id)) count++;
            }
        }
        return count;
    }

    private static int slotOf(Minecraft mc, Item item) {
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) return i;
        }
        return -1;
    }

    private void countsAre(String what, Item item, int inventory, int journal) {
        act(mc -> {
            int have = inInventory(mc, item), kept = inJournal(screen(mc), item);
            Report.check(what + " (" + have + " carried, " + kept + " in the journal)", have == inventory && kept == journal);
        });
    }

    private void pasteSlip() {
        clickBook(66 + 20, 200, 0);
    }

    private void pick(Item item) {
        act(mc -> {
            JournalScreen s = screen(mc);
            List<Integer> slots = s.pickerSlots();
            for (int i = 0; i < slots.size(); i++) {
                if (mc.player.getInventory().getItem(slots.get(i)).is(item)) {
                    mc.screen.mouseClicked(s.pickerCellX(i) + 8, s.pickerCellY(i) + 8, 0);
                    mc.screen.mouseReleased(s.pickerCellX(i) + 8, s.pickerCellY(i) + 8, 0);
                    return;
                }
            }
            Report.check("the picker offers " + item, false);
        });
        pause(2);
    }

    /** Clicks a spot on the page of the entry being written, plus some pages further on. */
    private void stick(int pagesOn, int dx, int dy) {
        act(mc -> {
            int x = pageLeft(screen(mc).editingPage() + pagesOn);
            mc.screen.mouseClicked(bookX(mc) + x + dx, bookY(mc) + 16 + dy, 0);
            mc.screen.mouseReleased(bookX(mc) + x + dx, bookY(mc) + 16 + dy, 0);
        });
        pause(10);
    }

    private void pasteOn(Item item, int dx, int dy) {
        pasteSlip();
        pick(item);
        stick(0, dx, dy);
    }

    private void forgePaste(java.util.function.Function<Minecraft, Integer> slot, java.util.function.Function<Minecraft, String> key) {
        act(mc -> {
            JournalScreen s = screen(mc);
            String journalId = s.journal().id;
            Entry entry = s.journal().entries.get(s.journal().entries.size() - 1);
            EJNetClient.send(EJPayloads.PASTE_KEEPSAKE, new EJPayloads.PasteKeepsake(journalId, entry.id, entry.type, UUID.randomUUID().toString(), slot.apply(mc), key.apply(mc), 0, 40, 60));
        });
        pause(4);
    }

    private static String keyOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void paste() {
        final String[] sword = new String[1];
        inWorld();
        // the last run may have left a peeled stick lying here
        command("kill @e[type=minecraft:item]");
        pause(5);
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("give @a minecraft:poppy 3");
        command("give @a minecraft:cornflower");
        command("give @a minecraft:feather");
        command("give @a minecraft:stick");
        command("give @a minecraft:string");
        command("give @a minecraft:sweet_berries 2");
        command("give @a minecraft:oak_stairs");
        command("give @a minecraft:shulker_box");
        //? if >=1.21 {
        /*command("give @a minecraft:diamond_sword[minecraft:custom_name='\"Old Blade\"',minecraft:enchantments={levels:{\"minecraft:sharpness\":3}},minecraft:damage=10]");
        *///?} else {
        command("give @a minecraft:diamond_sword{display:{Name:'\"Old Blade\"'},Enchantments:[{id:\"minecraft:sharpness\",lvl:3s}],Damage:10}");
        //?}
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();

        // Paste something in: a new scrapbook page, and the facing page becomes the menu
        clickChoice(pasteRow());
        check("Paste something in opens the picker", JournalScreen::pickerOpen);
        act(mc -> {
            Inventory inv = mc.player.getInventory();
            List<Integer> slots = screen(mc).pickerSlots();
            Report.check("the picker offers the poppy", slots.stream().anyMatch(slot -> inv.getItem(slot).is(Items.POPPY)));
            Report.check("the picker leaves out shulker boxes", slots.stream().noneMatch(slot -> inv.getItem(slot).is(Items.SHULKER_BOX)));
            Report.check("the picker leaves out journals", slots.stream().noneMatch(slot -> inv.getItem(slot).is(EJItems.JOURNAL)));
            var registries = mc.level.registryAccess();
            Report.check("bundles cannot be pasted", !Keepsakes.canPaste(new ItemStack(Items.BUNDLE), registries));
        });
        hover(-40, -6);
        shot("paste_picker");
        pick(Items.POPPY);
        check("picking an item puts it on the mouse", JournalScreen::carrying);
        act(mc -> hover0(mc, pageLeft(screen(mc).editingPage()) + 40, 16 + 60));
        pause(2);
        shot("paste_carrying");
        countsAre("nothing moves until you stick it down", Items.POPPY, 3, 0);
        stick(0, 40, 60);
        countsAre("sticking it down moves one item", Items.POPPY, 2, 1);
        check("the new page is a scrapbook entry", s -> s.journal().entries.size() == 1 && s.journal().entries.get(0).isScrapbook());
        check("you are still writing on it", s -> s.focusName().equals("title"));
        type("Keepsakes");
        hover(-40, -6);

        // more things on the same page, and Esc on the mouse means never mind
        pasteSlip();
        pick(Items.CORNFLOWER);
        press(GLFW.GLFW_KEY_ESCAPE);
        check("Esc puts it back", s -> !s.carrying() && s.focusName().equals("title"));
        countsAre("Esc moved nothing", Items.CORNFLOWER, 1, 0);
        pasteSlip();
        pick(Items.CORNFLOWER);
        act(mc -> {
            mc.screen.mouseClicked(300, 100, 1);
            mc.screen.mouseReleased(300, 100, 1);
        });
        pause(2);
        check("a right-click puts it back", s -> !s.carrying());
        countsAre("the right-click moved nothing", Items.CORNFLOWER, 1, 0);

        pasteOn(Items.CORNFLOWER, 96, 44);
        pasteOn(Items.FEATHER, 20, 90);
        pasteOn(Items.STICK, 96, 100);
        pasteOn(Items.STRING, 24, 134);
        pasteOn(Items.OAK_STAIRS, 96, 140);
        act(mc -> {
            ItemStack blade = mc.player.getInventory().getItem(slotOf(mc, Items.DIAMOND_SWORD));
            sword[0] = Keepsakes.write(blade, mc.level.registryAccess());
            Report.line("the sword is " + sword[0]);
        });
        pasteOn(Items.DIAMOND_SWORD, 60, 100);
        shot("paste_writing_page");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("the title is kept", s -> s.journal().entries.get(0).title.equals("Keepsakes"));
        hover(-40, -6);
        shot("paste_page");
        clickTab(Section.SCRAPBOOK);
        check("the S tab goes to it", s -> s.spread() == s.pageOfEntry(s.journal().entries.get(0).id) / 2);

        // hover shows the real item, and a right-click gets the very same item back
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = pageLeft(s.pageOfEntry(s.journal().entries.get(0).id));
            hover0(mc, x + 60, 16 + 100);
        });
        pause(2);
        shot("paste_hover_sword");
        hover(-40, -6);
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = pageLeft(s.pageOfEntry(s.journal().entries.get(0).id));
            mc.screen.mouseClicked(bookX(mc) + x + 60, bookY(mc) + 16 + 100, 1);
            mc.screen.mouseReleased(bookX(mc) + x + 60, bookY(mc) + 16 + 100, 1);
        });
        pause(10);
        countsAre("a right-click peels it off", Items.DIAMOND_SWORD, 1, 0);
        act(mc -> {
            ItemStack blade = mc.player.getInventory().getItem(slotOf(mc, Items.DIAMOND_SWORD));
            String now = Keepsakes.write(blade, mc.level.registryAccess());
            Report.check("the sword comes back exactly the same", now.equals(sword[0]));
        });

        // writing goes tight around what is taped down: your berry page
        String berries = "Check out these really awesome berries I found. They taste delicious but are very prickly. ";
        newNote("In the forest", berries);
        pasteOn(Items.SWEET_BERRIES, 100, 62);
        pause(4);
        check("no writing touches the berries", s -> s.linesUnderKeepsakes(s.editingPage()) == 0);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.editingPage(), lines = s.linesOnPage(page);
            boolean noGap = true;
            for (int n = 0; n < lines; n++) noGap &= s.lineRowOnPage(page, n) == n;
            Report.check("no row is lost beside the berries (" + lines + " lines)", noGap);
        });
        type("More words go right on beside them.");
        hover(-40, -6);
        pause(4);
        shot("paste_tight_berries");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);

        // a thing in the middle: the writing goes down both sides of it
        String words = "We left the river at first light and followed the old road north. ".repeat(4);
        newNote("A flower", words);
        pasteOn(Items.POPPY, 50, 50);
        pause(4);
        check("no writing touches the flower", s -> s.linesUnderKeepsakes(s.editingPage()) == 0);
        check("the writing runs on both sides of the flower", s -> {
            int page = s.editingPage(), lines = s.linesOnPage(page);
            for (int n = 1; n < lines; n++) {
                if (s.lineRowOnPage(page, n) == s.lineRowOnPage(page, n - 1)) return true;
            }
            return false;
        });
        check("the writing is all still there", s -> s.bodyText().equals(words));
        hover(-40, -6);
        shot("paste_text_flows");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("reading shows no writing on the flower either", s -> s.linesUnderKeepsakes(s.pageOfEntry(s.journal().entries.get(s.journal().entries.size() - 1).id)) == 0);
        shot("paste_text_flows_read");

        // the caret and keys work on the lines beside a thing
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry note = s.journal().entries.get(s.journal().entries.size() - 1);
            int x = pageLeft(s.pageOfEntry(note.id));
            mc.screen.mouseClicked(bookX(mc) + x + 5, bookY(mc) + 16 + 13 + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 5, bookY(mc) + 16 + 13 + 4, 0);
        });
        pause(8);
        for (int i = 0; i < 6; i++) press(GLFW.GLFW_KEY_DOWN);
        check("down goes through the lines beside the flower", s -> s.caretLine() >= 6);
        for (int i = 0; i < 6; i++) press(GLFW.GLFW_KEY_UP);
        check("up comes back", s -> s.caretLine() == 0);
        press(GLFW.GLFW_KEY_END);
        check("end stops at the end of the line", s -> s.caret() == s.lineTextOnPage(s.editingPage(), 0).length());
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);

        // a note with only a keepsake left stays
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry note = s.journal().entries.get(s.journal().entries.size() - 1);
            int x = pageLeft(s.pageOfEntry(note.id));
            mc.screen.mouseClicked(bookX(mc) + x + 20, bookY(mc) + 16 + 2, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 20, bookY(mc) + 16 + 2, 0);
        });
        pause(8);
        pressWith(GLFW.GLFW_KEY_A, false, true);
        press(GLFW.GLFW_KEY_BACKSPACE);
        press(GLFW.GLFW_KEY_TAB);
        pressWith(GLFW.GLFW_KEY_A, false, true);
        press(GLFW.GLFW_KEY_BACKSPACE);
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("a note with no words but a flower stays", s -> s.journal().entries.size() == 3 && inJournal(s, Items.POPPY) == 2);

        // peeling the only thing off a new page, while still writing on it, leaves no ghost
        clickChoice(pasteRow());
        pick(Items.DIAMOND_SWORD);
        stick(0, 40, 60);
        check("one thing on the new page", s -> s.keepsakesOnWriting() == 1);
        act(mc -> {
            int x = pageLeft(screen(mc).editingPage());
            mc.screen.mouseClicked(bookX(mc) + x + 40, bookY(mc) + 16 + 60, 1);
            mc.screen.mouseReleased(bookX(mc) + x + 40, bookY(mc) + 16 + 60, 1);
        });
        pause(10);
        check("peeling it leaves no ghost on the page", s -> s.keepsakesOnWriting() == 0);
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("the empty page is gone", s -> s.journal().entries.size() == 3);

        // a place: things can sit on the map, or on the blank page after it
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 4);
        pause(30);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry place = s.journal().entries.get(3);
            int x = pageLeft(s.pageOfEntry(place.id));
            mc.screen.mouseClicked(bookX(mc) + x + 20, bookY(mc) + 16 + 2, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 20, bookY(mc) + 16 + 2, 0);
        });
        pause(8);
        pasteOn(Items.POPPY, 100, 150);
        check("a thing on a place stays on the map, away from the writing", s -> s.journal().entries.get(3).keepsakes.get(0).y + 32 <= 116);
        hover(-40, -6);
        shot("paste_on_map");
        pasteSlip();
        pick(Items.DIAMOND_SWORD);
        act(mc -> {
            if (screen(mc).editingPage() % 2 == 1) {
                mc.screen.mouseClicked(bookX(mc) + 281, bookY(mc) + 179, 0);
                mc.screen.mouseReleased(bookX(mc) + 281, bookY(mc) + 179, 0);
            }
        });
        pause(5);
        final int[] blank = new int[1];
        act(mc -> {
            blank[0] = screen(mc).blankPage();
            hover0(mc, pageLeft(blank[0]) + 60, 16 + 60);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(blank[0]) + 60, bookY(mc) + 16 + 60, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(blank[0]) + 60, bookY(mc) + 16 + 60, 0);
        });
        pause(10);
        check("the blank page after your newest entry can take it", s -> s.pagesOfEntry(s.journal().entries.get(3).id) == 2);
        shot("paste_next_page");

        // deleting an entry gives everything back
        act(mc -> Report.line("before delete: " + inInventory(mc, Items.POPPY) + " poppies, " + inInventory(mc, Items.STRING) + " string"));
        clickBook(268, 200, 0);
        clickBook(268, 200, 0);
        pause(10);
        check("the place is gone", s -> s.journal().entries.size() == 3);
        countsAre("its things came back", Items.POPPY, 1, 2);

        // forged and repeated packets cannot make or lose an item
        forgePaste(mc -> -1, mc -> keyOf(Items.POPPY));
        forgePaste(mc -> 99, mc -> keyOf(Items.POPPY));
        forgePaste(mc -> slotOf(mc, Items.POPPY), mc -> keyOf(Items.STICK));
        countsAre("a bad slot or a wrong name moves nothing", Items.POPPY, 1, 2);
        forgePaste(mc -> slotOf(mc, Items.SHULKER_BOX), mc -> keyOf(Items.SHULKER_BOX));
        countsAre("a shulker box cannot be forced in", Items.SHULKER_BOX, 1, 0);
        forgePaste(mc -> mc.player.getInventory().selected, mc -> keyOf(EJItems.JOURNAL));
        check("the journal cannot go in itself", s -> inJournal(s, EJItems.JOURNAL) == 0);
        act(mc -> {
            for (int i = 0; i < 5; i++) {
                JournalScreen s = screen(mc);
                Entry entry = s.journal().entries.get(2);
                EJNetClient.send(EJPayloads.PASTE_KEEPSAKE, new EJPayloads.PasteKeepsake(s.journal().id, entry.id, entry.type, UUID.randomUUID().toString(), slotOf(mc, Items.POPPY), keyOf(Items.POPPY), 1, 40, 60));
            }
        });
        pause(10);
        countsAre("five pastes of one poppy move just one", Items.POPPY, 0, 3);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(2);
            Keepsake last = entry.keepsakes.get(entry.keepsakes.size() - 1);
            for (int i = 0; i < 5; i++) EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, entry.id, last.id));
        });
        pause(10);
        countsAre("five peels of one poppy give back just one", Items.POPPY, 1, 2);

        // no journal in hand, no moves
        act(mc -> mc.player.getInventory().selected = (mc.player.getInventory().selected + 1) % 9);
        pause(5);
        forgePaste(mc -> slotOf(mc, Items.POPPY), mc -> keyOf(Items.POPPY));
        countsAre("no journal in hand, no paste", Items.POPPY, 1, 2);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(2);
            EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, entry.id, entry.keepsakes.get(0).id));
        });
        pause(5);
        countsAre("no journal in hand, no peel", Items.POPPY, 1, 2);
        act(mc -> {
            var inv = mc.player.getInventory();
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(EJItems.JOURNAL)) inv.selected = i;
            }
        });
        pause(5);

        // a full inventory: the peeled item lands at your feet, it is never lost
        command("give @a minecraft:dirt 2304");
        pause(5);
        act(mc -> Report.line("sticks on the ground before the peel: " + sticksOnGround(mc)));
        act(mc -> {
            JournalScreen s = screen(mc);
            Keepsake stick = null;
            for (Keepsake k : s.journal().entries.get(0).keepsakes) {
                if (k.item.contains("minecraft:stick")) stick = k;
            }
            Report.line("peeling a stick with " + mc.player.getInventory().getFreeSlot() + " as the free slot");
            EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, s.journal().entries.get(0).id, stick.id));
        });
        pause(5);
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            String name = mc.player.getGameProfile().getName();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayerByName(name);
                int ground = 0;
                for (ItemEntity e : player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) {
                    if (e.getItem().is(Items.STICK)) ground += e.getItem().getCount();
                }
                Report.check("with a full bag the peeled stick lands at your feet (" + ground + " on the ground)", ground == 1 && inInventoryServer(player, Items.STICK) == 0);
            });
        });
        pause(5);
        command("clear @a minecraft:dirt");

        // the item's mod is gone: a scrap holds its place, and it stays until the mod is back
        act(mc -> {
            String id = screen(mc).journal().id;
            String name = mc.player.getGameProfile().getName();
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                Journal journal = JournalStore.get(server, id);
                Keepsake gone = new Keepsake();
                gone.id = UUID.randomUUID().toString();
                gone.item = "{count:1,id:\"gone_mod:old_thing\"}";
                gone.x = 90;
                gone.y = 60;
                journal.entries.get(0).keepsakes.add(gone);
                var player = server.getPlayerList().getPlayerByName(name);
                JournalService.commit(server, player, player.getMainHandItem(), journal);
            });
        });
        pause(10);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(0).id);
            while (s.spread() > page / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        shot("paste_scrap");
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, entry.id, entry.keepsakes.get(entry.keepsakes.size() - 1).id));
        });
        pause(8);
        check("a keepsake from a missing mod cannot be peeled, and is not lost", s -> s.journal().entries.get(0).keepsakes.get(s.journal().entries.get(0).keepsakes.size() - 1).item.contains("gone_mod"));

        act(mc -> mc.keyboardHandler.setClipboard(""));
        closeScreen();
        finish();
    }

    private static int sticksOnGround(Minecraft mc) {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayerByName(mc.player.getGameProfile().getName());
        int ground = 0;
        for (ItemEntity e : player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) {
            if (e.getItem().is(Items.STICK)) ground += e.getItem().getCount();
        }
        return ground;
    }

    private static int inInventoryServer(net.minecraft.server.level.ServerPlayer player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) count += inventory.getItem(i).getCount();
        }
        return count;
    }

    // ---- a crash must never leave an item in two places. Each script ends by killing the game, with no save.

    private void kill() {
        act(mc -> Report.line("killing the game with no save"));
        pause(2);
        act(mc -> Runtime.getRuntime().halt(0));
    }

    private void crash1() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("give @a minecraft:poppy 3");
        pause(10);
        openJournal();
        clickChoice(pasteRow());
        pick(Items.POPPY);
        stick(0, 40, 60);
        countsAre("first paste", Items.POPPY, 2, 1);
        // the same save the game runs every 5 minutes (/save-all only exists on a dedicated server)
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> server.saveEverything(true, true, true));
        });
        pause(30);
        act(mc -> Report.line("saved with 2 carried and 1 in the journal"));
        pasteSlip();
        pick(Items.POPPY);
        stick(0, 90, 60);
        countsAre("second paste, not saved", Items.POPPY, 1, 2);
        pause(10);
        kill();
    }

    private void crash2() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        pause(10);
        countsAre("after the crash both sides went back to the last save, so there is no extra poppy", Items.POPPY, 2, 1);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, entry.id, entry.keepsakes.get(0).id));
        });
        pause(10);
        countsAre("a peel, not saved", Items.POPPY, 3, 0);
        pause(10);
        kill();
    }

    private void crash3() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        pause(10);
        countsAre("after this crash the peel is undone on both sides too", Items.POPPY, 2, 1);
        closeScreen();
        finish();
    }

    private void crash4() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        pause(10);
        countsAre("a normal quit and reload keeps it all", Items.POPPY, 2, 1);
        closeScreen();
        finish();
    }

    // ---- two players on a slow server

    private void alice5() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        act(mc -> mc.player.setXRot(0));
        openJournal();
        clickChoice(1);
        pause(10);
        act(mc -> Report.check("the frame opens on a dedicated server", Viewfinder.isOpen()));
        shot("alice_viewfinder");
        clickInWorld();
        until(mc -> mc.screen instanceof JournalScreen s && s.sketchReady());
        pause(25);
        type("Seen from here");
        clickBook(220, 200, 0);
        until(mc -> screen(mc).journal().entries.size() == 1 && screen(mc).journal().entries.get(0).isSketch());
        pause(10);
        shot("alice_sketch");
        clickChoice(drawRow());
        pause(5);
        strokeOn(10, 10, 50, 50, 0);
        type("");
        clickDone();
        until(mc -> screen(mc).journal().entries.size() == 2 && screen(mc).journal().entries.get(1).isDrawing());
        pause(10);
        shot("alice_drawing");
        clickChoice(pasteRow());
        pick(Items.POPPY);
        stick(0, 40, 60);
        until(mc -> screen(mc).journal().entries.size() == 3);
        pause(10);
        clickThing(s -> s.journal().entries.get(2).id, 0);
        type("for bob to see");
        clickDone();
        until(mc -> "for bob to see".equals(screen(mc).journal().entries.get(2).keepsakes.get(0).note));
        pause(10);
        shot("alice_note");
        closeScreen();
        act(mc -> Report.line("alice is waiting for bob"));
        until(mc -> online(mc) == 2);
        until(mc -> online(mc) == 1);
        pause(20);
        openJournal();
        pause(20);
        check("bob could not change her sketch", s -> s.journal().entries.size() == 3 && s.journal().entries.get(0).title.equals("Seen from here"));
        check("bob could not change her drawing", s -> s.journal().entries.get(1).title.isEmpty() && !"bobs-picture".equals(s.journal().entries.get(1).map));
        check("bob could not change her note or move her poppy", s -> {
            Keepsake k = s.journal().entries.get(2).keepsakes.get(0);
            return "for bob to see".equals(k.note) && k.x == 40 - 16 && k.y == 60 - 16;
        });
        closeScreen();
        finish();
    }

    private void bob5() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(40);
        openJournal();
        clickTab(Section.DRAWINGS);
        pause(60);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            Report.check("bob gets alice's sketch from the server", entry.isSketch() && MapImages.getSketch(s.journal().id, entry.map) != null);
        });
        clickTab(Section.DRAWINGS);
        pause(60);
        check("bob gets alice's drawing from the server", s -> MapImages.getDrawing(s.journal().id, s.journal().entries.get(1).map) != null && MapImages.drawingPixels(s.journal().entries.get(1).map)[10 * PixelCanvas.SIZE + 10] != 0);
        check("bob cannot draw in her journal", s -> s.desk() == null);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(1).id);
            int x = bookX(mc) + (page % 2 == 0 ? 17 : 166) + 60, y = bookY(mc) + 16 + 12 + 60;
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
        });
        pause(5);
        check("a click on her picture does nothing for bob", s -> s.desk() == null);
        shot("bob_sees_drawing");
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(1);
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(s.journal().id, entry.id, UUID.randomUUID().toString(), "Bob was here", "", new PixelCanvas(null).pack()));
            Report.line("bob sent a forged drawing");
        });
        pause(30);
        hover(-40, -6);
        shot("bob_sees_sketch");
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            EJNetClient.send(EJPayloads.SAVE_SKETCH, new EJPayloads.SaveImage(s.journal().id, entry.id, entry.map, "Bob was here", "", SketchDrawer.pack(new byte[SketchDrawer.W * SketchDrawer.H])));
            Report.line("bob sent a forged sketch");
        });
        pause(20);
        clickTab(Section.SCRAPBOOK);
        pause(20);
        check("bob reads alice's note beside her poppy", s -> s.noteOf(s.journal().entries.get(2).id, 0).equals("for bob to see") && s.noteSpot(s.journal().entries.get(2).id, 0) != null);
        clickThing(s -> s.journal().entries.get(2).id, 0);
        check("a click on her poppy does not open it for bob", s -> s.focusName().isEmpty() && s.editingId().isEmpty());
        dragThing(s -> s.journal().entries.get(2).id, 0, 30, 30);
        check("bob cannot drag her poppy", s -> s.keepsakeSpot(s.journal().entries.get(2).id, 0)[1] == 40 - 16 && s.editingId().isEmpty());
        hover(-40, -6);
        shot("bob_sees_note");
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(2);
            Keepsake k = entry.keepsakes.get(0);
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(s.journal().id, entry.id, k.id, 0, 90, 90, "Bob was here"));
            Report.line("bob sent a forged note");
        });
        pause(40);
        closeScreen();
        finish();
    }

    private void alice4() {
        inWorld();
        until(ClientScript::hasJournal);
        pause(20);
        openJournal();
        clickChoice(pasteRow());
        until(mc -> screen(mc).pickerOpen());
        pick(Items.POPPY);
        stick(0, 40, 60);
        until(mc -> inJournal(screen(mc), Items.POPPY) == 1);
        countsAre("alice pasted one", Items.POPPY, 2, 1);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            int slot = slotOf(mc, Items.POPPY);
            for (int i = 0; i < 5; i++) {
                EJNetClient.send(EJPayloads.PASTE_KEEPSAKE, new EJPayloads.PasteKeepsake(s.journal().id, entry.id, entry.type, UUID.randomUUID().toString(), slot, keyOf(Items.POPPY), 0, 40 + i * 10, 90));
            }
        });
        until(mc -> inJournal(screen(mc), Items.POPPY) >= 3);
        pause(40);
        countsAre("five fast pastes on a slow server move only what she has", Items.POPPY, 0, 3);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            String id = entry.keepsakes.get(0).id;
            for (int i = 0; i < 5; i++) EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, entry.id, id));
        });
        until(mc -> inInventory(mc, Items.POPPY) >= 1);
        pause(40);
        countsAre("five fast peels of one poppy give back one", Items.POPPY, 1, 2);
        shot("alice_keepsakes");
        closeScreen();
        act(mc -> Report.line("alice is waiting for bob"));
        until(mc -> online(mc) == 2);
        until(mc -> online(mc) == 1);
        pause(40);
        openJournal();
        pause(20);
        countsAre("bob could not touch her keepsakes", Items.POPPY, 1, 2);
        shot("alice_after_bob");
        closeScreen();
        finish();
    }

    private void bob4() {
        inWorld();
        until(ClientScript::hasJournal);
        until(mc -> inInventory(mc, Items.POPPY) == 2);
        pause(40);
        openJournal();
        pause(40);
        shot("bob_sees_keepsakes");
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry entry = s.journal().entries.get(0);
            EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(s.journal().id, entry.id, entry.keepsakes.get(0).id));
            EJNetClient.send(EJPayloads.PASTE_KEEPSAKE, new EJPayloads.PasteKeepsake(s.journal().id, entry.id, entry.type, UUID.randomUUID().toString(), slotOf(mc, Items.POPPY), keyOf(Items.POPPY), 0, 10, 10));
            Report.line("bob sent a forged peel and a forged paste");
        });
        pause(60);
        act(mc -> Report.check("bob got nothing and lost nothing (" + inInventory(mc, Items.POPPY) + " poppies)", inInventory(mc, Items.POPPY) == 2));
        check("bob's copy shows no change", s -> inJournal(s, Items.POPPY) == 2);
        closeScreen();
        finish();
    }

    // ---- step 7: loose layouts, notes beside things, and what pokes out

    private static String fixedId(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static Keepsake keepsakeOf(MinecraftServer server, String name, Item item, int page, int x, int y, String note) {
        Keepsake k = new Keepsake();
        k.id = fixedId("thing " + name);
        k.item = Keepsakes.write(new ItemStack(item), server.registryAccess());
        k.page = page;
        k.x = x;
        k.y = y;
        k.note = note;
        return k;
    }

    private static Entry entryOf(String name, String type, long day, String title, String text) {
        Entry e = new Entry();
        e.id = fixedId("entry " + name);
        e.type = type;
        e.day = day;
        e.title = title;
        e.text = text;
        return e;
    }

    /** A house on a hill, in the sketch's own format. */
    private static byte[] testSketch() {
        int w = SketchDrawer.W;
        byte[] lv = new byte[w * SketchDrawer.H];
        for (int x = 0; x < w; x++) lv[(70 + x / 10) * w + x] = 1;
        for (int y = 36; y < 72; y++) {
            lv[y * w + 40] = 1;
            lv[y * w + 84] = 1;
        }
        for (int x = 40; x <= 84; x++) lv[36 * w + x] = 1;
        for (int i = 0; i < 22; i++) {
            lv[(36 - i) * w + 40 + i] = 1;
            lv[(36 - i) * w + 84 - i] = 1;
        }
        return SketchDrawer.pack(lv);
    }

    private static byte[] testDrawing(int seed) {
        PixelCanvas canvas = new PixelCanvas(null);
        canvas.beginStroke();
        canvas.line(10, 50, 54, 50, DyeColor.GREEN.getId() + 1);
        canvas.line(20 + seed, 50, 32, 20, DyeColor.BROWN.getId() + 1);
        canvas.line(32, 20, 44 - seed, 50, DyeColor.BROWN.getId() + 1);
        canvas.line(48, 8, 52, 12, DyeColor.YELLOW.getId() + 1);
        canvas.endStroke();
        return canvas.pack();
    }

    /** Every kind of entry, many times, so every layout shows up. The IDs are fixed, so each run makes the same pages. */
    private void fillForLayouts() {
        act(mc -> {
            String id = JournalItem.id(mc.player.getMainHandItem());
            String name = mc.player.getGameProfile().getName();
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                Journal journal = JournalStore.get(server, id);
                String map = journal.entries.get(0).map;
                String walk = "We left the river at first light and followed the old road north. ";
                String[] texts = {"Short day. Rain.", walk.repeat(2), walk.repeat(5), "see: Stonehollow\nGood bread at the inn.", walk.repeat(9), "The smith owes me a good stick."};
                for (int i = 0; i < 8; i++) {
                    journal.entries.add(entryOf("note " + i, Entry.NOTE, 3 + i, i % 3 == 1 ? "On the long road home" : "Note " + i, texts[i % texts.length]));
                }
                for (int i = 0; i < 4; i++) {
                    Entry e = entryOf("place " + i, Entry.LOCATION, 20 + i, i == 2 ? "Windswept Gravelly Hills" : "Camp " + i, i % 2 == 0 ? "Safe to sleep here." : "");
                    e.dim = "minecraft:overworld";
                    e.x = -976 + 300 * i;
                    e.y = 70;
                    e.z = 150;
                    e.map = map;
                    journal.entries.add(e);
                }
                for (int i = 0; i < 6; i++) {
                    Entry e = entryOf("sketch " + i, Entry.SKETCH, 30 + i, i == 3 ? "" : "The first house " + i, i % 2 == 0 ? "Built with a good view of the road. The thatch is new." : "");
                    e.map = fixedId("sketch image " + i);
                    JournalStore.saveMap(server, journal.id, e.map, testSketch());
                    journal.entries.add(e);
                }
                for (int i = 0; i < 6; i++) {
                    Entry e = entryOf("drawing " + i, Entry.DRAWING, 40 + i, i == 4 ? "" : "Camp " + i, i % 2 == 0 ? "Our tent." : "");
                    e.map = fixedId("drawing image " + i);
                    JournalStore.saveMap(server, journal.id, e.map, testDrawing(i));
                    journal.entries.add(e);
                }
                Entry board = entryOf("scrap 0", Entry.SCRAPBOOK, 50, "Keepsakes", "");
                board.keepsakes.add(keepsakeOf(server, "poppy", Items.POPPY, 0, 16, 18, "poppy, by the smith's door"));
                board.keepsakes.add(keepsakeOf(server, "cornflower", Items.CORNFLOWER, 0, 84, 58, "up on the hill, the day it rained"));
                board.keepsakes.add(keepsakeOf(server, "feather", Items.FEATHER, 0, 10, 96, "a chicken owed me"));
                board.keepsakes.add(keepsakeOf(server, "stick", Items.STICK, 0, 88, 112, "good stick"));
                journal.entries.add(board);
                Entry close = entryOf("scrap 1", Entry.SCRAPBOOK, 51, "Two poppies", "Picked these on the way back to camp, by the old wall.");
                close.keepsakes.add(keepsakeOf(server, "poppy a", Items.POPPY, 0, 30, 40, "the first one"));
                close.keepsakes.add(keepsakeOf(server, "poppy b", Items.POPPY, 0, 62, 44, "and one more"));
                journal.entries.add(close);
                Entry edge = entryOf("scrap 2", Entry.SCRAPBOOK, 52, "By the edge", walk.repeat(3));
                edge.keepsakes.add(keepsakeOf(server, "string", Items.STRING, 0, 96, 60, "off a bad night"));
                edge.keepsakes.add(keepsakeOf(server, "berries", Items.SWEET_BERRIES, 1, 4, 20, "too sour to eat"));
                journal.entries.add(edge);
                Entry noted = entryOf("note with things", Entry.NOTE, 53, "Found on the road", walk.repeat(4));
                noted.keepsakes.add(keepsakeOf(server, "feather b", Items.FEATHER, 0, 44, 50, "found here"));
                noted.keepsakes.add(keepsakeOf(server, "stick b", Items.STICK, 0, 4, 100, ""));
                journal.entries.add(noted);
                Entry placed = entryOf("place with thing", Entry.LOCATION, 54, "Stonehollow", "Good water, bad bread.");
                placed.dim = "minecraft:overworld";
                placed.x = -976;
                placed.y = 67;
                placed.z = 150;
                placed.map = map;
                placed.keepsakes.add(keepsakeOf(server, "poppy c", Items.POPPY, 0, 70, 40, "picked by the door"));
                journal.entries.add(placed);
                Entry sketched = entryOf("sketch with thing", Entry.SKETCH, 55, "The smith's house", "Came in past the river bend. Nobody home.");
                sketched.map = fixedId("sketch image thing");
                JournalStore.saveMap(server, journal.id, sketched.map, testSketch());
                sketched.keepsakes.add(keepsakeOf(server, "cornflower b", Items.CORNFLOWER, 0, 10, 116, "from the garden"));
                journal.entries.add(sketched);
                var player = server.getPlayerList().getPlayerByName(name);
                JournalService.commit(server, player, player.getMainHandItem(), journal);
            });
        });
        pause(20);
    }

    /** Random pages of things with random notes, the same each run, to find what a hand-made page misses. On real pages, things go in open space. On stress pages, they pile on each other. */
    private void fuzzForLayouts(int count, boolean pileUp) {
        act(mc -> {
            String id = JournalItem.id(mc.player.getMainHandItem());
            String name = mc.player.getGameProfile().getName();
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                Journal journal = JournalStore.get(server, id);
                java.util.Random r = new java.util.Random(pileUp ? 7 : 11);
                Item[] things = {Items.POPPY, Items.CORNFLOWER, Items.FEATHER, Items.STICK, Items.STRING, Items.SWEET_BERRIES, Items.DANDELION, Items.BONE, Items.IRON_INGOT, Items.OAK_PLANKS, Items.FLINT, Items.PAPER};
                String[] phrases = {"good stick", "found by the river", "a chicken owed me", "poppy, by the smith's door", "up on the hill, the day it rained", "too sour", "from the old mine, near the very bottom", "for Bob", "the last one he had, and he gave it to me"};
                String walk = "We left the river at first light and followed the old road north. ";
                String kind = pileUp ? "pile " : "fuzz ";
                for (int i = 0; i < count; i++) {
                    boolean scrap = r.nextInt(3) > 0;
                    Entry e = entryOf(kind + i, scrap ? Entry.SCRAPBOOK : Entry.NOTE, 60 + i, (pileUp ? "Pile " : "Page ") + i, r.nextBoolean() ? walk.repeat(1 + r.nextInt(4)) : "");
                    int n = 1 + r.nextInt(scrap ? 4 : 2);
                    List<int[]> taken = new java.util.ArrayList<>();
                    for (int k = 0; k < n; k++) {
                        int x = r.nextInt(98), y = 15 + r.nextInt(105);
                        // a person pastes into open space: look for a spot clear of the others
                        for (int tries = 0; !pileUp && tries < 30 && overlapsAny(taken, x, y); tries++) {
                            x = r.nextInt(98);
                            y = 15 + r.nextInt(105);
                        }
                        if (!pileUp && overlapsAny(taken, x, y)) continue;
                        taken.add(new int[]{x, y});
                        String note = r.nextInt(4) == 0 ? "" : phrases[r.nextInt(phrases.length)];
                        e.keepsakes.add(keepsakeOf(server, kind + i + " " + k, things[r.nextInt(things.length)], 0, x, y, note));
                    }
                    journal.entries.add(e);
                }
                var player = server.getPlayerList().getPlayerByName(name);
                JournalService.commit(server, player, player.getMainHandItem(), journal);
            });
        });
        pause(20);
    }

    private static boolean overlapsAny(List<int[]> taken, int x, int y) {
        for (int[] t : taken) {
            if (Math.abs(t[0] - x) < 34 && Math.abs(t[1] - y) < 34) return true;
        }
        return false;
    }

    /** Counts what touches on the pages of these entries, and writes each problem down. Returns touches by notes, by writing, and off the page. */
    private static int[] countProblems(JournalScreen s, String titleStart) {
        int[] counts = new int[4];
        for (int page = 0; page < s.pageCount(); page++) {
            if (!s.pageKind(page).equals("ENTRY")) continue;
            Entry entry = s.journal().entry(s.entryOnPage(page));
            if (entry == null || !entry.title.startsWith(titleStart)) continue;
            counts[3] += s.notesCutOnPage(page);
            for (String problem : s.layoutProblems(page)) {
                Report.line("page " + page + " (" + entry.title + "): " + problem);
                if (problem.contains("leaves the page")) counts[2]++;
                else if (problem.contains("note")) counts[0]++;
                else counts[1]++;
            }
        }
        return counts;
    }

    private void layouts() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 1);
        pause(30);
        fillForLayouts();
        closeScreen();
        openJournal();
        pause(20);
        act(mc -> {
            JournalScreen s = screen(mc);
            for (Entry e : s.journal().entries) Report.line(e.type + " \"" + e.title + "\": " + s.layoutOf(e.id));
            java.util.function.BiPredicate<String, String> has = (type, what) -> s.journal().entries.stream().anyMatch(e -> e.type.equals(type) && s.layoutOf(e.id).contains(what));
            Report.check("some sketches have their title under the picture, some over it", has.test(Entry.SKETCH, "under the picture") && has.test(Entry.SKETCH, "title at"));
            Report.check("some drawings have their title under the picture, some over it", has.test(Entry.DRAWING, "under the picture") && has.test(Entry.DRAWING, "title at"));
            Report.check("some notes put the day at the right, some right after the title", has.test(Entry.NOTE, "day right") && has.test(Entry.NOTE, "day after"));
            Report.check("some notes start their first line further in", has.test(Entry.NOTE, "first line in"));
            Report.check("some scrapbook pages center the title", has.test(Entry.SCRAPBOOK, "centered title"));
        });
        // every page: nothing written touches anything else, and everything stays on the page
        flipAll();
        act(mc -> {
            JournalScreen s = screen(mc);
            int bad = 0;
            for (int page = 0; page < s.pageCount(); page++) {
                if (!s.pageKind(page).equals("ENTRY")) continue;
                for (String problem : s.layoutProblems(page)) {
                    Report.line("page " + page + " (" + s.journal().entry(s.entryOnPage(page)).title + "): " + problem);
                    bad++;
                }
            }
            Report.check("on every page, nothing written touches anything else, and nothing leaves the page (" + bad + " problems)", bad == 0);
        });
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry board = s.journal().entry(fixedId("entry scrap 0"));
            int[] poppy = s.noteSpot(board.id, 0), cornflower = s.noteSpot(board.id, 1);
            Report.check("a note goes beside its thing where there is room", poppy != null && poppy[2] == 0);
            Report.check("a thing near the right edge gets its note on its left", cornflower != null && cornflower[2] == 1);
            Entry close = s.journal().entry(fixedId("entry scrap 1"));
            Report.check("two things side by side both get their notes", s.noteSpot(close.id, 0) != null && s.noteSpot(close.id, 1) != null);
            Entry noted = s.journal().entry(fixedId("entry note with things"));
            Report.check("a thing with no note shows no note", s.noteSpot(noted.id, 1) == null && s.noteSpot(noted.id, 0) != null);
        });
        // a look at every spread
        act(mc -> {
            while (screen(mc).spread() > 0) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        hover(-40, -6);
        for (int i = 0; i < 26; i++) {
            shot("layout_spread_" + i);
            act(mc -> keyQuiet(mc, GLFW.GLFW_KEY_RIGHT));
            pause(6);
        }
        // the title and the writing are where the layout says, for clicks too
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry sketch = s.journal().entries.stream().filter(e -> e.isSketch() && s.layoutOf(e.id).contains("under the picture") && !e.title.isEmpty()).findFirst().orElseThrow();
            while (s.spread() < s.pageOfEntry(sketch.id) / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
            while (s.spread() > s.pageOfEntry(sketch.id) / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            Report.line("clicking the title of \"" + sketch.title + "\"");
        });
        clickTitleOf(s -> s.journal().entries.stream().filter(e -> e.isSketch() && s.layoutOf(e.id).contains("under the picture") && !e.title.isEmpty()).findFirst().orElseThrow().id);
        check("a click on a title under a picture writes in the title", s -> s.focusName().equals("title"));
        press(GLFW.GLFW_KEY_END);
        type(" II");
        press(GLFW.GLFW_KEY_ENTER);
        check("enter goes from that title to the writing", s -> s.focusName().equals("body"));
        hover(-40, -6);
        shot("layout_title_under_picture");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("the title under the picture is kept", s -> s.journal().entries.stream().anyMatch(e -> e.isSketch() && e.title.endsWith(" II")));
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry drawing = s.journal().entries.stream().filter(e -> e.isDrawing() && s.layoutOf(e.id).contains("under the picture")).findFirst().orElseThrow();
            while (s.spread() < s.pageOfEntry(drawing.id) / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
            while (s.spread() > s.pageOfEntry(drawing.id) / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(30);
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry drawing = s.journal().entries.stream().filter(e -> e.isDrawing() && s.layoutOf(e.id).contains("under the picture")).findFirst().orElseThrow();
            int page = s.pageOfEntry(drawing.id);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(page) + 60, bookY(mc) + 16 + 40, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(page) + 60, bookY(mc) + 16 + 40, 0);
        });
        pause(5);
        check("a drawing with its title under it opens from the picture", s -> s.desk() != null);
        check("its canvas sits at the top of the page", s -> s.canvasCellY(0) - bookY(Minecraft.getInstance()) - 16 < 4);
        strokeOn(5, 60, 20, 60, 0);
        hover(-40, -6);
        shot("layout_drawing_title_under");
        clickDone();
        pause(10);
        check("it is kept", s -> s.desk() == null);

        // 40 random pages the way a person makes them, then 40 with things piled on each other
        final int fuzz = 40;
        fuzzForLayouts(fuzz, false);
        fuzzForLayouts(fuzz, true);
        closeScreen();
        openJournal();
        pause(30);
        flipAll();
        final int[] first = new int[1];
        act(mc -> {
            JournalScreen s = screen(mc);
            first[0] = s.pageOfEntry(fixedId("entry fuzz 0"));
            int withNotes = 0;
            for (Entry e : s.journal().entries) {
                if (e.title.startsWith("Page ")) for (Keepsake k : e.keepsakes) if (k.note != null && !k.note.isEmpty()) withNotes++;
            }
            int[] real = countProblems(s, "Page ");
            Report.line("on " + fuzz + " random pages with " + withNotes + " notes: " + real[0] + " touches by a note, " + real[1] + " by writing, " + real[2] + " off the page, " + real[3] + " notes cut short for room");
            Report.check("on random pages, no note touches anything", real[0] == 0);
            Report.check("on random pages, writing never touches a thing or a note", real[1] == 0);
            Report.check("on random pages, nothing leaves the page", real[2] == 0);
            int[] pile = countProblems(s, "Pile ");
            Report.line("on " + fuzz + " pages with things piled up: " + pile[0] + " touches by a note, " + pile[1] + " by writing, " + pile[2] + " off the page, " + pile[3] + " notes cut short for room");
            Report.check("even with things piled up, writing never touches anything", pile[1] == 0 && pile[2] == 0);
        });
        act(mc -> {
            JournalScreen s = screen(mc);
            while (s.spread() > first[0] / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < first[0] / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        hover(-40, -6);
        for (int i = 0; i < 24; i++) {
            shot("layout_random_" + i);
            act(mc -> keyQuiet(mc, GLFW.GLFW_KEY_RIGHT));
            pause(6);
        }
        closeScreen();
        finish();
    }

    /** Presses on a thing and lets go without moving, the way a person clicks it. */
    private void clickThing(java.util.function.Function<JournalScreen, String> entry, int index) {
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(entry.apply(s), index);
            mc.screen.mouseClicked(bookX(mc) + at[3], bookY(mc) + at[4], 0);
            mc.screen.mouseReleased(bookX(mc) + at[3], bookY(mc) + at[4], 0);
        });
        pause(5);
    }

    /** Presses on a thing, drags it by dx and dy in a few steps, and lets go. */
    private void dragThing(java.util.function.Function<JournalScreen, String> entry, int index, int dx, int dy) {
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(entry.apply(s), index);
            double x = bookX(mc) + at[3], y = bookY(mc) + at[4];
            mc.screen.mouseClicked(x, y, 0);
            for (int i = 1; i <= 4; i++) mc.screen.mouseDragged(x + dx * i / 4.0, y + dy * i / 4.0, 0, dx / 4.0, dy / 4.0);
        });
        pause(2);
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(entry.apply(s), index);
            mc.screen.mouseReleased(bookX(mc) + at[3] + dx, bookY(mc) + at[4] + dy, 0);
        });
        pause(10);
    }

    /** Checks that nothing on the page touches, and writes down what does. */
    private void checkNoTouch(String what, java.util.function.ToIntFunction<JournalScreen> page) {
        act(mc -> {
            JournalScreen s = screen(mc);
            List<String> problems = s.layoutProblems(page.applyAsInt(s));
            problems.forEach(p -> Report.line("  " + p));
            Report.check(what, problems.isEmpty());
        });
    }

    private static String newest(JournalScreen s) {
        return s.journal().entries.isEmpty() ? s.editingId() : s.journal().entries.get(s.journal().entries.size() - 1).id;
    }

    private void notes() {
        inWorld();
        command("kill @e[type=minecraft:item]");
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("give @a minecraft:poppy 2");
        command("give @a minecraft:cornflower");
        command("give @a minecraft:feather");
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();

        // a thing on a new page, then a click on it writes its note
        clickChoice(pasteRow());
        pick(Items.POPPY);
        stick(0, 30, 50);
        countsAre("the poppy is in", Items.POPPY, 1, 1);
        type("Keepsakes");
        clickThing(ClientScript::newest, 0);
        check("a click on a thing writes the note beside it", s -> s.focusName().equals("caption"));
        hover(-40, -6);
        shot("notes_empty");
        type("poppy, by the smith's door");
        check("the note takes the words", s -> s.noteOf(newest(s), 0).equals("poppy, by the smith's door"));
        check("the note sits beside the thing", s -> s.noteSpot(newest(s), 0) != null && s.noteSpot(newest(s), 0)[2] == 0);
        shot("notes_typing");
        type(" and a lot more words that will not fit in three short lines at all");
        check("a note stops when it is full", s -> s.noteOf(newest(s), 0).length() < 60 && s.noteOf(newest(s), 0).startsWith("poppy, by the smith's door and"));
        checkNoTouch("a full note still touches nothing", JournalScreen::editingPage);
        pressWith(GLFW.GLFW_KEY_A, false, true);
        type("poppy, by the smith's door");
        clip("two\nlines");
        pressWith(GLFW.GLFW_KEY_V, false, true);
        check("a pasted line break stays out of a note", s -> !s.noteOf(newest(s), 0).contains("\n"));
        pressWith(GLFW.GLFW_KEY_Z, false, true);
        check("undo works in a note", s -> s.noteOf(newest(s), 0).equals("poppy, by the smith's door"));
        press(GLFW.GLFW_KEY_ENTER);
        check("enter goes from the note to the writing", s -> s.focusName().equals("body"));
        type("Picked on the way home. The smith waved from his door, and his dog barked at me the whole way down the hill.");
        check("the writing goes around the thing", s -> s.linesUnderKeepsakes(s.editingPage()) == 0);
        checkNoTouch("the writing goes around the note", JournalScreen::editingPage);
        hover(-40, -6);
        shot("notes_writing_around");

        // a second thing right beside the first: its note keeps clear of the first one
        pasteSlip();
        pick(Items.CORNFLOWER);
        stick(0, 92, 60);
        clickThing(ClientScript::newest, 1);
        type("up on the hill, the day it rained");
        check("a note never cuts what you write, wherever it sits", s -> s.noteOf(newest(s), 1).equals("up on the hill, the day it rained"));
        checkNoTouch("the second note keeps clear of everything", JournalScreen::editingPage);
        check("a thing with no room on its right gets its note somewhere else", s -> s.noteSpot(newest(s), 1)[2] != 0);
        hover(-40, -6);
        shot("notes_two");
        clickDone();
        pause(10);
        check("Done keeps both notes", s -> s.journal().entries.size() == 1
                && "poppy, by the smith's door".equals(s.journal().entries.get(0).keepsakes.get(0).note)
                && "up on the hill, the day it rained".equals(s.journal().entries.get(0).keepsakes.get(1).note));
        shot("notes_kept");
        closeScreen();
        openJournal();
        pause(10);
        check("the notes are still there when the book opens again", s -> s.noteOf(newest(s), 0).equals("poppy, by the smith's door"));

        // drag a thing: its note goes with it, and the server keeps the new spot
        final int[] was = new int[5];
        act(mc -> System.arraycopy(screen(mc).keepsakeSpot(newest(screen(mc)), 0), 0, was, 0, 5));
        dragThing(ClientScript::newest, 0, 10, 60);
        check("dragging a thing moves it", s -> s.keepsakeSpot(newest(s), 0)[2] > was[2] + 40);
        check("dragging a thing starts writing", s -> !s.editingId().isEmpty() && !s.grabbing());
        check("its note goes with it", s -> s.noteSpot(newest(s), 0) != null && s.noteSpot(newest(s), 0)[1] > was[4]);
        checkNoTouch("nothing touches after the move", JournalScreen::editingPage);
        hover(-40, -6);
        shot("notes_moved");
        act(mc -> {
            String id = JournalItem.id(mc.player.getMainHandItem());
            MinecraftServer server = mc.getSingleplayerServer();
            int y = screen(mc).keepsakeSpot(newest(screen(mc)), 0)[2];
            server.execute(() -> Report.check("the server keeps the new spot", JournalStore.get(server, id).entries.get(0).keepsakes.get(0).y == y));
        });
        pause(5);

        // a short wobble is still a click
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(newest(s), 1);
            mc.screen.mouseClicked(bookX(mc) + at[3], bookY(mc) + at[4], 0);
            mc.screen.mouseDragged(bookX(mc) + at[3] + 1, bookY(mc) + at[4] + 1, 0, 1, 1);
            mc.screen.mouseReleased(bookX(mc) + at[3] + 1, bookY(mc) + at[4] + 1, 0);
        });
        pause(5);
        check("a wobble of a pixel is a click, not a move", s -> s.focusName().equals("caption") && s.keepsakeSpot(newest(s), 1)[1] == 92 - 16);

        // let go off the page, and it goes back
        act(mc -> System.arraycopy(screen(mc).keepsakeSpot(newest(screen(mc)), 1), 0, was, 0, 5));
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(newest(s), 1);
            mc.screen.mouseClicked(bookX(mc) + at[3], bookY(mc) + at[4], 0);
            mc.screen.mouseDragged(bookX(mc) + at[3] + 20, bookY(mc) + at[4], 0, 20, 0);
            mc.screen.mouseDragged(4, 4, 0, -100, -100);
        });
        pause(2);
        hover(-40, -6);
        shot("notes_dragging_off");
        act(mc -> mc.screen.mouseReleased(4, 4, 0));
        pause(5);
        check("let go off your pages, a thing goes back", s -> s.keepsakeSpot(newest(s), 1)[1] == was[1] && s.keepsakeSpot(newest(s), 1)[2] == was[2]);

        // onto the blank page after it
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(newest(s), 1);
            int page = s.pageOfEntry(newest(s));
            int to = bookX(mc) + pageLeft(page + 1) + 60;
            mc.screen.mouseClicked(bookX(mc) + at[3], bookY(mc) + at[4], 0);
            mc.screen.mouseDragged(bookX(mc) + at[3] + 10, bookY(mc) + at[4], 0, 10, 0);
            mc.screen.mouseDragged(to, bookY(mc) + 80, 0, 0, 0);
            mc.screen.mouseReleased(to, bookY(mc) + 80, 0);
        });
        pause(10);
        check("a thing can move to the blank page after the newest entry", s -> s.keepsakeSpot(newest(s), 1)[0] == 1);
        check("its note goes with it there", s -> s.noteSpot(newest(s), 1) != null);
        hover(-40, -6);
        shot("notes_next_page");
        clickDone();
        pause(10);

        // reading: a click on a note writes in it
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.noteSpot(newest(s), 0);
            mc.screen.mouseClicked(bookX(mc) + at[0] + 2, bookY(mc) + at[1] + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + at[0] + 2, bookY(mc) + at[1] + 4, 0);
        });
        pause(5);
        check("a click on a note while reading writes in that note", s -> s.focusName().equals("caption"));
        press(GLFW.GLFW_KEY_HOME);
        type("a ");
        check("the click put the caret in the note", s -> s.noteOf(newest(s), 0).startsWith("a poppy"));
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        check("Esc keeps the note and stops writing", s -> s.focusName().isEmpty() && s.journal().entries.get(0).keepsakes.get(0).note.startsWith("a poppy"));

        // find looks in the notes too
        act(mc -> {
            JournalScreen s = screen(mc);
            int places = firstPageOfKind(s, "PLACES");
            while (s.spread() > places / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            mc.screen.mouseClicked(bookX(mc) + pageLeft(places) + 10, bookY(mc) + 170, 0);
            mc.screen.mouseReleased(bookX(mc) + pageLeft(places) + 10, bookY(mc) + 170, 0);
        });
        pause(5);
        type("smith's door");
        press(GLFW.GLFW_KEY_ENTER);
        pause(3);
        check("find looks in the notes beside things", s -> s.rowsOnPage(firstPageOfKind(s, "PLACES")) == 1);
        shot("notes_find");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(3);

        // the server does not take a forged note
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry e = s.journal().entries.get(0);
            Keepsake k = e.keepsakes.get(0);
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(s.journal().id, e.id, k.id, k.page, k.x, k.y, "§cred\nline " + "x".repeat(200)));
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(s.journal().id, e.id, UUID.randomUUID().toString(), 0, 10, 10, "no such thing"));
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(s.journal().id, UUID.randomUUID().toString(), k.id, 0, 10, 10, "no such entry"));
        });
        pause(10);
        check("a forged note loses colour codes and line breaks and stays short", s -> {
            String note = s.journal().entries.get(0).keepsakes.get(0).note;
            return !note.contains("§") && !note.contains("\n") && note.length() <= 80;
        });
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry e = s.journal().entries.get(0);
            Keepsake k = e.keepsakes.get(0);
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(s.journal().id, e.id, k.id, -5, 9999, -100, "poppy"));
        });
        pause(10);
        check("a forged spot is kept on the page", s -> {
            Keepsake k = s.journal().entries.get(0).keepsakes.get(0);
            return k.page == 0 && k.x == 129 && k.y == -8;
        });
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry e = s.journal().entries.get(0);
            Keepsake k = e.keepsakes.get(0);
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(s.journal().id, e.id, k.id, 0, 20, 40, "poppy, by the smith's door"));
        });
        pause(10);
        countsAre("no note or move made or lost an item", Items.POPPY, 1, 1);

        // peeling a thing off while its note is open
        clickTab(Section.SCRAPBOOK);
        clickThing(ClientScript::newest, 0);
        check("its note is open", s -> s.focusName().equals("caption"));
        act(mc -> {
            JournalScreen s = screen(mc);
            int[] at = s.keepsakeSpot(newest(s), 0);
            mc.screen.mouseClicked(bookX(mc) + at[3], bookY(mc) + at[4], 1);
            mc.screen.mouseReleased(bookX(mc) + at[3], bookY(mc) + at[4], 1);
        });
        pause(10);
        countsAre("peeling it off gives it back", Items.POPPY, 2, 0);
        check("its note closes with it", s -> !s.focusName().equals("caption"));
        type("x");
        pause(2);
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);
        closeScreen();
        finish();
    }

    private void wear() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 1);
        pause(30);
        closeScreen();
        String[] onlyPlace = {"", "", "", "map", "map"};
        String[] withNote = {"", "", "letter", "letter map", "letter map"};
        String[] withNotes = {"", "", "letter", "letter map", "letter map letter"};
        for (int stage = 0; stage < 5; stage++) wearCheck(stage, onlyPlace[stage], "a place", null);
        addEntries(1, 0);
        for (int stage = 0; stage < 5; stage++) wearCheck(stage, withNote[stage], "a place and a note", null);
        addEntries(1, 0);
        for (int stage = 0; stage < 5; stage++) wearCheck(stage, withNotes[stage], "a place and two notes", "wear_" + stage);
        command("journal wear auto");
        finish();
    }

    private void wearCheck(int stage, String expected, String content, String picture) {
        command("journal wear " + stage);
        pause(5);
        openJournal();
        pause(25);
        hover(-40, -6);
        check("a book at wear " + stage + " with " + content + " shows " + (expected.isEmpty() ? "no loose papers" : expected), s -> s.loosePapers().equals(expected));
        act(mc -> Report.line("shows: " + screen(mc).loosePapers()));
        if (picture != null) shot(picture);
        closeScreen();
    }

    // ---- step 8: the link with Location Tooltip. This one runs with Location Tooltip in run-LT/mods.

    private static Object lt(String className, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Class<?> c = Class.forName("com.fugginbeenus.locationtooltip." + className);
        Method m = c.getMethod(method, types);
        return m.invoke(null, args);
    }

    /** The name on Location Tooltip's pill right now. */
    private static String hudTitle() {
        try {
            Field f = Class.forName("com.fugginbeenus.locationtooltip.hud.LocationHudOverlay").getDeclaredField("currentTitle");
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (ReflectiveOperationException e) {
            return "?" + e;
        }
    }

    /** The pills other mods add to Location Tooltip right now, each {label, icon}. */
    @SuppressWarnings("unchecked")
    private static List<String[]> pills() {
        try {
            return (List<String[]>) lt("api.LocationTooltipApi", "pills", new Class<?>[0]);
        } catch (ReflectiveOperationException e) {
            return List.<String[]>of(new String[]{"?" + e, null});
        }
    }

    private static List<String> pillLabels() {
        return pills().stream().map(p -> p[0]).toList();
    }

    private static void scroll(Minecraft mc, double amount) {
        //? if >=1.21 {
        /*mc.screen.mouseScrolled(100, 100, 0, amount);
        *///?} else {
        mc.screen.mouseScrolled(100, 100, amount);
        //?}
    }

    private static int dyeColour(DyeColor color) {
        //? if >=1.21 {
        /*return color.getTextureDiffuseColor() & 0xFFFFFF;
        *///?} else {
        float[] c = color.getTextureDiffuseColors();
        return Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
        //?}
    }

    private static ClipContext ray(ServerLevel level, Vec3 from, Vec3 to) {
        //? if >=1.21 {
        /*return new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        *///?} else {
        return new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, level.getServer().getPlayerList().getPlayers().get(0));
        //?}
    }

    private static ResourceLocation id(String id) {
        //? if >=1.21 {
        /*return ResourceLocation.parse(id);
        *///?} else {
        return new ResourceLocation(id);
        //?}
    }

    /** How far apart two boxes are, in pixels. 0 when they touch or cover each other. */
    private static int gap(int[] a, int[] b) {
        int dx = Math.max(0, Math.max(b[0] - a[2], a[0] - b[2])), dy = Math.max(0, Math.max(b[1] - a[3], a[1] - b[3]));
        return Math.max(dx, dy);
    }

    /** A written name from Location Tooltip's long one is cut short with "..." and fits its room. */
    private static boolean fitsShort(Minecraft mc, JournalScreen s) {
        return s.markNames().stream().anyMatch(n -> n.name().startsWith("Lookout") && n.box() != null && n.box()[2] - n.box()[0] - 2 <= 60);
    }

    /** The first line of the screen's hover text right now, or "". */
    private static String tooltipOf(JournalScreen s) {
        try {
            Field f = JournalScreen.class.getDeclaredField("tooltip");
            f.setAccessible(true);
            List<?> lines = (List<?>) f.get(s);
            return lines.isEmpty() ? "" : ((net.minecraft.network.chat.Component) lines.get(0)).getString();
        } catch (ReflectiveOperationException e) {
            return "?" + e;
        }
    }

    /** Turns to an entry's page and clicks its way back, the row under the map. */
    private void clickWayBackOf(java.util.function.Function<JournalScreen, String> id) {
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(id.apply(s));
            while (s.spread() > page / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(id.apply(s));
            int x = page % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + 10, bookY(mc) + 16 + 127 + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 10, bookY(mc) + 16 + 127 + 4, 0);
        });
        pause(20);
    }

    private static Entry newestPlace(JournalScreen s) {
        Entry best = null;
        for (Entry e : s.journal().entries) if (e.isLocation()) best = e;
        return best;
    }

    private void markHere() {
        act(mc -> Report.line("marking a place at " + mc.player.blockPosition().toShortString()));
        final int[] before = new int[1];
        act(mc -> before[0] = screen(mc).journal().entries.size());
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == before[0] + 1);
        pause(30);
    }

    private void link() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("ltregion structures villagenames players on");
        act(mc -> Report.check("Location Tooltip is here, with the link", com.fugginbeenus.explorersjournal.compat.LocationTooltipCompat.linked()));
        command("tp @s -975 67 150 0 0");
        pause(120);
        openJournal();

        // a village: the place takes Location Tooltip's name, and remembers which region it is
        markHere();
        check("a place in a village takes Location Tooltip's name for it", s -> {
            Entry e = newestPlace(s);
            Report.line("village place: " + e.title + ", region " + e.place + ", icon " + e.marker + ", nameable " + e.nameable);
            return e.place != null && e.title.equals(e.placeName) && e.marker != null && e.marker.contains("village");
        });
        check("with the server's say-so, you may name a village nobody named", s -> newestPlace(s).nameable);
        closeScreen();

        // your own region gives its name to a place in it
        command("ltregion createbox \"Old Camp\" -1004 60 130 -994 80 140");
        command("tp @s -999 70 135 0 0");
        pause(40);
        openJournal();
        markHere();
        check("a place in your own region takes that region's name", s -> newestPlace(s).title.equals("Old Camp"));
        check("a banner is its map icon", s -> newestPlace(s).marker.endsWith("_banner"));
        closeScreen();

        // back in the village, the new map shows the camp
        command("tp @s -975 67 150 0 0");
        pause(40);
        openJournal();
        markHere();
        check("the map shows other places near it, with their names", s -> {
            Entry e = newestPlace(s);
            Report.line("marks: " + (e.marks == null ? "none" : e.marks.stream().map(m -> m.name + " " + m.marker + " at " + m.x + "," + m.y).toList()));
            return e.marks != null && e.marks.stream().anyMatch(m -> m.name.equals("Old Camp"));
        });
        hover(-40, -6);
        shot("link_map_marks");
        act(mc -> {
            JournalScreen s = screen(mc);
            int places = firstPageOfKind(s, "PLACES");
            while (s.spread() > places / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(5);
        shot("link_place_list");
        closeScreen();

        // many places near one spot: the six nearest, and their names never cover each other
        String[][] more = {
                {"Bridge", "-957 60 128 -953 80 132"}, {"Lookout Tower Over The Old River", "-942 60 153 -938 80 157"},
                {"Old Well", "-1007 60 108 -1003 80 112"}, {"North Field", "-980 60 85 -970 80 95"},
                {"Mill", "-1047 60 178 -1043 80 182"}, {"Fishing Spot", "-937 60 218 -933 80 222"},
                {"Ruins", "-877 60 68 -873 80 72"}, {"Hunting Lodge", "-1077 60 228 -1073 80 232"}};
        for (String[] r : more) command("ltregion createbox \"" + r[0] + "\" " + r[1]);
        command("tp @s -975 67 150 0 0");
        pause(20);
        openJournal();
        markHere();
        check("a map shows the six nearest places, no more", s -> {
            Entry e = newestPlace(s);
            Report.line("marks: " + (e.marks == null ? "none" : e.marks.stream().map(m -> m.name).toList()));
            return e.marks != null && e.marks.size() == 6 && e.marks.stream().noneMatch(m -> m.name.equals("Ruins") || m.name.equals("Hunting Lodge") || m.name.equals("Fishing Spot"));
        });
        act(mc -> {
            List<JournalScreen.MarkName> marks = screen(mc).markNames();
            List<JournalScreen.MarkName> named = marks.stream().filter(n -> n.box() != null).toList();
            Report.line("names written on the maps: " + named.stream().map(JournalScreen.MarkName::name).toList() + ", left off: " + marks.stream().filter(n -> n.box() == null).map(JournalScreen.MarkName::name).toList());
            boolean apart = true, own = true;
            for (JournalScreen.MarkName a : named) {
                for (JournalScreen.MarkName b : named) {
                    if (a != b && gap(a.box(), b.box()) == 0) apart = false;
                }
                // a name is nearer its own icon than any other, so it reads as that icon's name
                for (JournalScreen.MarkName b : marks) {
                    if (b.icon() != a.icon() && gap(a.box(), b.icon()) <= gap(a.box(), a.icon())) {
                        own = false;
                        Report.line(a.name() + " sits as near the icon of " + b.name());
                    }
                }
            }
            Report.check("the names on a map never cover each other", apart);
            Report.check("each name sits nearest its own icon", own);
            Report.check("a long name is cut short to fit", screen(mc).markNames().size() > 0 && fitsShort(mc, screen(mc)));
            int[] icon = marks.get(marks.size() - 1).icon();
            hover0(mc, (icon[0] + icon[2]) / 2 - bookX(mc), (icon[1] + icon[3]) / 2 - bookY(mc));
        });
        pause(3);
        act(mc -> {
            List<JournalScreen.MarkName> marks = screen(mc).markNames();
            String want = marks.get(marks.size() - 1).name();
            String shown = tooltipOf(screen(mc));
            Report.check("hovering a map icon shows its place's name (" + shown + ")", shown.equals(want));
        });
        hover(-40, -6);
        shot("link_many_marks");
        closeScreen();

        // the region is renamed while the book is shut: the old name is crossed out, the new one written beside it
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            String name = mc.player.getGameProfile().getName();
            server.execute(() -> {
                try {
                    ServerPlayer player = server.getPlayerList().getPlayerByName(name);
                    Object mgr = lt("region.RegionManager", "of", new Class<?>[]{MinecraftServer.class}, server);
                    Object region = mgr.getClass().getMethod("smallestContaining", ResourceLocation.class, BlockPos.class).invoke(mgr, Level.OVERWORLD.location(), new BlockPos(-999, 70, 135));
                    String id = (String) region.getClass().getField("id").get(region);
                    mgr.getClass().getMethod("renameRegion", ServerPlayer.class, String.class, String.class, java.util.Map.class).invoke(mgr, player, id, "Riverside Camp", java.util.Map.of());
                    Report.line("renamed the camp's region");
                } catch (ReflectiveOperationException e) {
                    Report.check("the test can rename a region, it threw " + e, false);
                }
            });
        });
        pause(10);
        openJournal();
        pause(10);
        check("a renamed region shows on its place", s -> s.journal().entries.stream().anyMatch(e -> "Old Camp".equals(e.placeName) && "Riverside Camp".equals(e.renamed)));
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry camp = s.journal().entries.stream().filter(e -> "Old Camp".equals(e.placeName)).findFirst().orElseThrow();
            int page = s.pageOfEntry(camp.id);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
            while (s.spread() > page / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(10);
        hover(-40, -6);
        shot("link_renamed");

        // naming the village for everyone, from the journal
        act(mc -> {
            JournalScreen s = screen(mc);
            Entry village = s.journal().entries.get(0);
            int page = s.pageOfEntry(village.id);
            while (s.spread() > page / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        clickTitleOf(s -> s.journal().entries.get(0).id);
        check("a click on the village's title writes in it", s -> s.focusName().equals("title"));
        pressWith(GLFW.GLFW_KEY_A, false, true);
        type("Riverwatch");
        hover(8 + 20, 200);
        shot("link_name_it");
        clickBook(8 + 20, 200, 0);
        pause(20);
        check("the village takes the name for everyone", s -> {
            try {
                Object name = lt("api.LocationTooltipApi", "nameOf", new Class<?>[]{MinecraftServer.class, String.class}, Minecraft.getInstance().getSingleplayerServer(), s.journal().entries.get(0).place);
                Report.line("Location Tooltip calls it " + name);
                return "Riverwatch".equals(name);
            } catch (ReflectiveOperationException e) {
                return false;
            }
        });
        check("and the place keeps the new name, not crossed out", s -> {
            Entry e = s.journal().entries.get(0);
            return e.title.equals("Riverwatch") && "Riverwatch".equals(e.placeName) && e.renamed == null;
        });
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);

        // pin a place: Location Tooltip shows it on a pill, with how far and which way
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(0).id);
            int x = page % 2 == 0 ? 17 : 166;
            mc.screen.mouseClicked(bookX(mc) + x + 10, bookY(mc) + 16 + 127 + 4, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 10, bookY(mc) + 16 + 127 + 4, 0);
        });
        pause(20);
        check("a click on the way back pins the place", s -> s.journal().pinned.equals(s.journal().entries.get(0).id));
        act(mc -> {
            Entry village = screen(mc).journal().entries.get(0);
            pinnedAt = new int[]{village.x, village.z};
            List<String[]> now = pills();
            Report.check("standing at the place, its pill says so (" + pillLabels() + ")", now.size() == 1 && now.get(0)[0].equals("Riverwatch, here") && now.get(0)[1] == null);
        });
        hover(-40, -6);
        shot("link_pinned_page");
        closeScreen();
        command("tp @s -940 70 190 0 0");
        pause(40);
        act(mc -> {
            List<String> labels = pillLabels();
            Report.line("pills: " + labels);
            Report.check("the pinned place has a pill, with its name and how far", labels.size() == 1 && labels.get(0).matches("Riverwatch \\d+m"));
        });
        // vanilla's compass on the pill points the way: up when you face the place, left when it is on your left
        for (int[] turn : new int[][]{{0, 16}, {90, 8}, {180, 0}, {270, 24}, {45, 12}}) {
            act(mc -> {
                double dx = pinnedAt[0] + 0.5 - mc.player.getX(), dz = pinnedAt[1] + 0.5 - mc.player.getZ();
                mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)) + turn[0]);
            });
            pause(2);
            act(mc -> {
                String icon = pills().isEmpty() ? null : pills().get(0)[1];
                Report.check("turned " + turn[0] + " degrees right of the place, the compass is " + icon, icon != null && icon.equals(String.format("minecraft:textures/item/compass_%02d.png", turn[1])));
            });
        }
        shot("link_pin_pill");

        // the pill follows the journal: put it in a chest and the pill goes, take it back and the pill comes back
        command("setblock -940 70 193 chest");
        command("item replace block -940 70 193 container.0 from entity @s weapon.mainhand");
        command("item replace entity @s weapon.mainhand with air");
        pause(60);
        act(mc -> Report.check("with the journal put away in a chest, the pill goes (" + pillLabels() + ")", pillLabels().isEmpty()));
        command("item replace entity @s weapon.mainhand from block -940 70 193 container.0");
        command("setblock -940 70 193 air");
        pause(60);
        act(mc -> Report.check("with the journal back in hand, the pill comes back (" + pillLabels() + ")", pillLabels().size() == 1));

        // in another dimension, the pill waits
        command("execute in minecraft:the_nether run tp @s 0 129 0");
        pause(60);
        act(mc -> Report.check("in the Nether, the pinned place has no pill (" + pillLabels() + ")", pillLabels().isEmpty()));

        // your own name for a place in the wilderness, on Location Tooltip's pill, for you alone
        command("execute in minecraft:overworld run tp @s -700 80 300 0 0");
        pause(80);
        act(mc -> mc.player.connection.sendCommand("tp @s -700 ~ 300"));
        pause(10);
        openJournal();
        markHere();
        act(mc -> {
            Entry e = newestPlace(screen(mc));
            lastPlaceTitle = e.title;
            Report.line("wilderness place: " + e.title);
            Report.check("a wilderness place remembers the name it had at first", e.title.equals(e.placeName) && e.place == null);
        });
        closeScreen();
        command("tp @s -690 ~ 300 0 0");
        pause(30);
        act(mc -> Report.check("a place you have not named leaves the wilderness as it is (" + hudTitle() + ")", !hudTitle().equals(lastPlaceTitle)));
        openJournal();
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(newestPlace(s).id);
            while (s.spread() > page / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        clickTitleOf(s -> newestPlace(s).id);
        check("a click on the wilderness place's title writes in it", s -> s.focusName().equals("title"));
        pressWith(GLFW.GLFW_KEY_A, false, true);
        type("Wolf Den");
        clickDone();
        pause(20);
        check("the place takes your name for it", s -> newestPlace(s).title.equals("Wolf Den"));
        closeScreen();
        command("tp @s -680 ~ 300 0 0");
        pause(30);
        act(mc -> Report.check("near a place you named, the pill shows your name for it (" + hudTitle() + ")", hudTitle().equals("Wolf Den")));
        shot("link_wild_name");
        command("tp @s -500 ~ 300 0 0");
        pause(40);
        act(mc -> Report.check("further off, it is the wilderness again (" + hudTitle() + ")", !hudTitle().equals("Wolf Den")));

        // Location Tooltip by itself: with the journal's hooks taken out, it acts as it did before the link
        List<List<Object>> hooks = new java.util.ArrayList<>();
        act(mc -> {
            try {
                for (String name : new String[]{"WILDERNESS", "PILLS"}) {
                    Field f = Class.forName("com.fugginbeenus.locationtooltip.api.LocationTooltipApi").getDeclaredField(name);
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked") List<Object> list = (List<Object>) f.get(null);
                    hooks.add(new java.util.ArrayList<>(list));
                    list.clear();
                }
            } catch (ReflectiveOperationException e) {
                Report.check("the test can take the journal's hooks out, it threw " + e, false);
            }
        });
        act(mc -> Report.check("alone, it shows no pinned place pill", pills().isEmpty()));
        command("tp @s -680 ~ 300 0 0");
        pause(30);
        act(mc -> Report.check("alone, your named place is the wilderness (" + hudTitle() + ")", !hudTitle().equals("Wolf Den")));
        command("tp @s -975 67 150 0 0");
        pause(40);
        act(mc -> Report.check("alone, a village still has its name (" + hudTitle() + ")", hudTitle().equals("Riverwatch")));
        act(mc -> {
            try {
                for (int i = 0; i < 2; i++) {
                    Field f = Class.forName("com.fugginbeenus.locationtooltip.api.LocationTooltipApi").getDeclaredField(i == 0 ? "WILDERNESS" : "PILLS");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked") List<Object> list = (List<Object>) f.get(null);
                    list.addAll(hooks.get(i));
                }
            } catch (ReflectiveOperationException | IndexOutOfBoundsException e) {
                Report.check("the test can put the hooks back, it threw " + e, false);
            }
        });
        act(mc -> Report.check("with the journal's hooks back, the pill is back", pills().size() == 1));

        // pin the camp instead: Location Tooltip renamed it, so its pill has the new name
        openJournal();
        clickWayBackOf(s -> s.journal().entries.stream().filter(e -> "Old Camp".equals(e.placeName)).findFirst().orElseThrow().id);
        check("a click on another place moves the pin to it", s -> s.journal().entry(s.journal().pinned) != null && "Old Camp".equals(s.journal().entry(s.journal().pinned).placeName));
        act(mc -> Report.check("the pill has the place's new name (" + pillLabels() + ")", pillLabels().size() == 1 && pillLabels().get(0).startsWith("Riverside Camp")));

        // take the pin out
        clickWayBackOf(s -> s.journal().pinned);
        check("a second click takes the pin out", s -> s.journal().pinned.isEmpty());
        act(mc -> Report.check("and its pill is gone", pillLabels().isEmpty()));
        closeScreen();
        finish();
    }

    // ---- step 9: the config file and the /journal command

    private static Path configFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("explorersjournal.json");
    }

    private void writeConfig(String json) {
        act(mc -> {
            try {
                Files.writeString(configFile(), json);
            } catch (java.io.IOException e) {
                Report.check("the script can write the config, it threw " + e, false);
            }
        });
    }

    /** Runs a command as this player with every permission, and keeps what it says. */
    private void commandSaying(String command, List<String> said) {
        act(mc -> {
            said.clear();
            MinecraftServer server = mc.getSingleplayerServer();
            String name = mc.player.getGameProfile().getName();
            server.execute(() -> {
                net.minecraft.commands.CommandSource catcher = new net.minecraft.commands.CommandSource() {
                    @Override
                    public void sendSystemMessage(Component message) {
                        said.add(message.getString());
                    }

                    @Override
                    public boolean acceptsSuccess() {
                        return true;
                    }

                    @Override
                    public boolean acceptsFailure() {
                        return true;
                    }

                    @Override
                    public boolean shouldInformAdmins() {
                        return false;
                    }
                };
                server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayerByName(name).createCommandSourceStack().withPermission(4).withSource(catcher), command);
            });
        });
        pause(5);
    }

    private static int countOf(Minecraft mc, Item item) {
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) count += mc.player.getInventory().getItem(slot).getCount();
        }
        return count;
    }

    private static int driftOf(String method, Object... args) {
        try {
            Class<?> drift = Class.forName("com.fugginbeenus.explorersjournal.client.Drift");
            Class<?>[] types = new Class<?>[args.length];
            java.util.Arrays.fill(types, int.class);
            Method m = drift.getDeclaredMethod(method, types);
            m.setAccessible(true);
            return (int) m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            return -99;
        }
    }

    private void rules() {
        List<String> said = new java.util.concurrent.CopyOnWriteArrayList<>();
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("give @a poppy 2");
        act(mc -> {
            try {
                String text = Files.readString(configFile());
                Report.check("the config file is there, with its defaults", text.contains("\"textDrift\": true") && text.contains("\"othersCanRead\": true")
                        && text.contains("\"pasteCostsPaper\": false") && text.contains("\"maxEntries\": 500"));
            } catch (java.io.IOException e) {
                Report.check("the config file is there, it threw " + e, false);
            }
        });
        act(mc -> {
            boolean drifts = false;
            for (int seed = 1; seed < 40; seed++) drifts |= driftOf("shift", seed, 0) != 0;
            Report.check("by default, writing drifts a little", drifts);
        });
        openJournal();
        newNote("First", "one");
        endWriting();
        newNote("Second", "two");
        endWriting();
        newNote("Third", "three");
        endWriting();
        check("3 notes are in the book", s -> s.journal().entries.size() == 3);
        check("by default, 3 entries do not wear the book", s -> s.wearShown() == 0);
        closeScreen();

        // new rules, read again with /journal reload
        writeConfig("{\"textDrift\": false, \"othersCanRead\": false, \"pasteCostsPaper\": true, \"maxEntries\": 3, \"wearAt\": [1, 2, 3, 3]}");
        commandSaying("journal reload", said);
        act(mc -> Report.check("/journal reload reads the file again (" + said + ")", said.stream().anyMatch(l -> l.contains("reloaded"))));
        act(mc -> {
            boolean flat = true;
            for (int seed = 1; seed < 40; seed++) flat &= driftOf("shift", seed, 0) == 0 && driftOf("gap", seed, 0, 1) == 0;
            Report.check("with textDrift off, writing is straight", flat);
        });
        openJournal();
        check("the server's wear steps set how worn the book looks (stage " + 3 + ")", s -> s.wearShown() == 3);
        check("at the most entries, the journal says it is full", JournalScreen::isFull);
        hover(-40, -6);
        act(mc -> {
            JournalScreen s = screen(mc);
            while (s.spread() < s.blankPage() / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        shot("rules_full");
        clickChoice(writeNoteRow());
        check("and a click on its blank page starts nothing", s -> s.editingId().isEmpty() && s.journal().entries.size() == 3);

        // the tape costs 1 paper
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(2).id);
            while (s.spread() > page / 2) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(5);
        clickTitleOf(s -> s.journal().entries.get(2).id);
        check("you can still write in an entry of a full journal", s -> !s.editingId().isEmpty());
        pasteSlip();
        check("the paste menu opens", JournalScreen::pickerOpen);
        shot("rules_no_paper");
        pick(Items.POPPY);
        check("with no paper, you cannot pick a thing to tape", s -> s.pickerOpen() && !s.carrying());
        command("give @a paper 2");
        pause(10);
        pick(Items.POPPY);
        check("with paper, you can", JournalScreen::carrying);
        stick(0, 60, 90);
        pause(10);
        check("the poppy is taped in", s -> s.journal().entries.get(2).keepsakes.size() == 1);
        act(mc -> Report.check("and the tape used 1 paper (" + countOf(mc, Items.PAPER) + " left)", countOf(mc, Items.PAPER) == 1 && countOf(mc, Items.POPPY) == 1));
        endWriting();
        closeScreen();

        // the commands
        commandSaying("journal list Dev", said);
        act(mc -> {
            Report.line("list says: " + said);
            Report.check("/journal list shows the journal and its entries", said.size() == 4 && said.get(0).contains("3 entries") && said.get(1).startsWith("1. First, note") && said.get(3).contains("1 taped in"));
        });
        commandSaying("journal remove Dev 3", said);
        act(mc -> Report.check("/journal remove takes an entry out (" + said + ")", said.stream().anyMatch(l -> l.startsWith("Removed 3. Third"))));
        pause(10);
        act(mc -> Report.check("and what was taped in comes back to the player", countOf(mc, Items.POPPY) == 2));
        commandSaying("journal remove Dev 9", said);
        act(mc -> Report.check("a number past the end is refused (" + said + ")", said.stream().anyMatch(l -> l.contains("has 2 entries"))));
        openJournal();
        check("the journal has 2 entries now", s -> s.journal().entries.size() == 2);
        closeScreen();
        commandSaying("journal wear 4", said);
        openJournal();
        check("/journal wear 4 shows the most worn book", s -> s.wearShown() == 4);
        closeScreen();
        commandSaying("journal wear auto", said);
        openJournal();
        // the book once held 3 entries, and wear never goes back
        check("/journal wear auto goes back to its own wear", s -> s.wearShown() == 3);
        closeScreen();

        // a journal that is not yours stays shut, when journals are private
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            String id = com.fugginbeenus.explorersjournal.item.JournalItem.id(mc.player.getMainHandItem());
            server.execute(() -> JournalStore.get(server, id).owner = UUID.randomUUID().toString());
        });
        pause(5);
        act(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        pause(30);
        act(mc -> Report.check("with othersCanRead off, someone else's journal stays shut", !(mc.screen instanceof JournalScreen)));
        writeConfig("{\"othersCanRead\": true}");
        commandSaying("journal reload", said);
        act(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        until(mc -> mc.screen instanceof JournalScreen);
        pause(10);
        check("with it on, you can read it", s -> s.journal().entries.size() == 2);
        clickChoice(writeNoteRow());
        check("and not write in it", s -> s.editingId().isEmpty());
        shot("rules_reading");
        closeScreen();
        act(mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            String id = com.fugginbeenus.explorersjournal.item.JournalItem.id(mc.player.getMainHandItem());
            String me = mc.player.getUUID().toString();
            server.execute(() -> JournalStore.get(server, id).owner = me);
        });

        // a broken file changes nothing
        writeConfig("{\"maxEntries\": 3,,, oops");
        commandSaying("journal reload", said);
        act(mc -> Report.check("a broken config file is refused, and says so (" + said + ")", said.stream().anyMatch(l -> l.contains("mistake"))));
        act(mc -> Report.check("and the rules stay as they were", com.fugginbeenus.explorersjournal.config.EJConfig.get().maxEntries == 500 && com.fugginbeenus.explorersjournal.config.EJConfig.get().othersCanRead));

        // back to the defaults for the other scripts
        act(mc -> {
            try {
                Files.deleteIfExists(configFile());
            } catch (java.io.IOException e) {
                Report.check("the script can remove the config, it threw " + e, false);
            }
        });
        commandSaying("journal reload", said);
        finish();
    }

    /** An old Location Tooltip has no door for other mods, so the journal goes on alone. This one runs with Location Tooltip 0.7.0 in run-LT07/mods. */
    private void linkOld() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("ltregion structures villagenames players on");
        act(mc -> Report.check("Location Tooltip is here", net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("locationtooltip")));
        act(mc -> Report.check("but it is too old for the link, so the journal goes on alone", !com.fugginbeenus.explorersjournal.compat.LocationTooltipCompat.linked()));
        command("tp @s -975 67 150 0 0");
        pause(120);
        openJournal();
        markHere();
        check("a place takes the journal's own name for it, with no marks", s -> {
            Entry e = newestPlace(s);
            Report.line("place: " + e.title + ", region " + e.place + ", marks " + e.marks);
            return e.place == null && e.marks == null && !e.title.isBlank() && e.title.equals(e.placeName);
        });
        act(mc -> Report.check("and the same village name as Location Tooltip shows (" + newestPlace(screen(mc)).title + ", " + hudTitle() + ")", newestPlace(screen(mc)).title.equals(hudTitle())));
        hover(-40, -6);
        shot("old_lt_place");
        closeScreen();
        finish();
    }

    private String lastPlaceTitle = "";
    private int[] pinnedAt = {0, 0};

    // ---- a real Exposure photo. This one runs with Exposure in run-Photo/mods.

    private void photo() {
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 180 10");
        pause(100);
        command("exposure expose {id:\"ej_test\"}");
        pause(60);
        command("give @a exposure:photograph[exposure:photograph_frame={identifier:\"ej_test\"}]");
        pause(10);
        openJournal();
        hover(-40, -6);
        clickChoice(pasteRow());
        final Item photo = BuiltInRegistries.ITEM.get(EJId.of("exposure", "photograph"));
        check("the picker offers the photo", s -> s.pickerSlots().stream().anyMatch(slot -> Minecraft.getInstance().player.getInventory().getItem(slot).is(photo)));
        act(mc -> {
            ItemStack stacked = new ItemStack(BuiltInRegistries.ITEM.get(EJId.of("exposure", "stacked_photographs")));
            Report.check("the cannot_paste tag keeps stacks of photos out", !stacked.isEmpty() && !Keepsakes.canPaste(stacked, mc.level.registryAccess()));
        });
        shot("photo_picker");
        pick(photo);
        act(mc -> hover0(mc, pageLeft(screen(mc).editingPage()) + 60, 16 + 60));
        pause(2);
        shot("photo_carrying");
        stick(0, 60, 60);
        countsAre("the photo moves into the journal", photo, 0, 1);
        type("That hill");
        hover(-40, -6);
        press(GLFW.GLFW_KEY_ENTER);
        type("The view from the old road.");
        pause(40);
        shot("photo_writing");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(20);
        shot("photo_page");
        check("the writing went around the photo", s -> s.linesOnPage(s.pageOfEntry(s.journal().entries.get(0).id)) < 14);
        act(mc -> {
            JournalScreen s = screen(mc);
            hover0(mc, pageLeft(s.pageOfEntry(s.journal().entries.get(0).id)) + 60, 16 + 60);
        });
        pause(5);
        shot("photo_hover");

        // a note under a photo, with an arrow up to it
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = pageLeft(s.pageOfEntry(s.journal().entries.get(0).id));
            mc.screen.mouseClicked(bookX(mc) + x + 60, bookY(mc) + 16 + 60, 0);
            mc.screen.mouseReleased(bookX(mc) + x + 60, bookY(mc) + 16 + 60, 0);
        });
        pause(5);
        check("a click on a photo writes the note beside it", s -> s.focusName().equals("caption"));
        type("this fella followed me for days");
        check("a photo's note goes under it", s -> s.noteSpot(s.journal().entries.get(0).id, 0) != null && s.noteSpot(s.journal().entries.get(0).id, 0)[2] == 2);
        check("nothing touches on the photo page", s -> s.layoutProblems(s.pageOfEntry(s.journal().entries.get(0).id)).isEmpty());
        hover(-40, -6);
        shot("photo_note");
        press(GLFW.GLFW_KEY_ESCAPE);
        pause(10);

        // a worn book with a photo in it has a photo poking out
        command("journal wear 4");
        closeScreen();
        openJournal();
        pause(20);
        hover(-40, -6);
        check("a worn book with a photo has a photo poking out", s -> s.loosePapers().contains("photo"));
        shot("photo_poking_out");
        command("journal wear auto");
        closeScreen();
        openJournal();
        pause(10);
        act(mc -> {
            JournalScreen s = screen(mc);
            int x = pageLeft(s.pageOfEntry(s.journal().entries.get(0).id));
            mc.screen.mouseClicked(bookX(mc) + x + 60, bookY(mc) + 16 + 60, 1);
            mc.screen.mouseReleased(bookX(mc) + x + 60, bookY(mc) + 16 + 60, 1);
        });
        pause(10);
        countsAre("a right-click gives the photo back", photo, 1, 0);
        closeScreen();
        finish();
    }

    /** Puts the game's idea of the mouse on a spot of the book, so a screenshot shows the hover look. */
    private void hover(int dx, int dy) {
        act(mc -> hover0(mc, dx, dy));
        pause(2);
    }

    private static int lastEntryPage(JournalScreen s) {
        return s.pageOfEntry(s.journal().entries.get(s.journal().entries.size() - 1).id);
    }

    private void controls() {
        final int esc = GLFW.GLFW_KEY_ESCAPE;
        inWorld();
        command("clear @a");
        command("give @a explorersjournal:journal");
        command("tp @s -975 67 150 0 0");
        pause(80);
        openJournal();
        hover(-40, -6);
        shot("controls_new_book");
        check("a new book opens on the contents", s -> s.spread() == 0);

        // a place and a note to work with
        clickChoice(0);
        until(mc -> mc.screen instanceof JournalScreen s && s.journal().entries.size() == 1);
        pause(30);
        shot("controls_place");
        clickChoice(writeNoteRow());
        shot("controls_new_note");
        type("First note");
        press(GLFW.GLFW_KEY_ENTER);
        shot("controls_empty_body");
        type("Some words here.");
        hover(-40, -6);
        shot("controls_writing");
        hover(220, 200);
        shot("controls_hover_done");
        hover(268, 200);
        shot("controls_hover_delete");

        // Done stops writing and keeps it
        clickBook(220, 200, 0);
        pause(10);
        check("Done stops writing", s -> s.focusName().isEmpty());
        check("Done keeps the note", s -> s.journal().entries.size() == 2);

        // hover looks
        act(mc -> {
            while (screen(mc).spread() > 0) keyQuiet(mc, GLFW.GLFW_KEY_LEFT);
        });
        pause(2);
        hover(281, 179);
        shot("controls_hover_next_arrow");
        hover(312, Section.CONTENTS.y + 8);
        shot("controls_hover_tab");
        hover(130, 198);
        shot("controls_hover_blue_ribbon");
        hover(180, 205);
        shot("controls_hover_red_ribbon");

        // the page arrows
        final int[] at = new int[1];
        act(mc -> at[0] = screen(mc).spread());
        clickBook(30, 179, 0);
        check("no back arrow on the first page", s -> s.spread() == at[0]);
        clickBook(281, 179, 0);
        check("the next arrow turns the page", s -> s.spread() == at[0] + 1);
        clickBook(30, 179, 0);
        check("the back arrow turns back", s -> s.spread() == at[0]);

        // the feather starts writing
        clickTab(Section.NOTES);
        act(mc -> {
            int page = lastEntryPage(screen(mc));
            int x = page % 2 == 0 ? 17 + 129 - 18 + 8 : 166 + 2 + 8;
            mc.screen.mouseClicked(bookX(mc) + x, bookY(mc) + 171 + 8, 0);
            mc.screen.mouseReleased(bookX(mc) + x, bookY(mc) + 171 + 8, 0);
        });
        pause(5);
        check("the feather starts writing at the end of the note", s -> s.focusName().equals("body") && s.caret() == s.bodyText().length());
        press(esc);
        pause(10);
        act(mc -> {
            int page = lastEntryPage(screen(mc));
            hover0(mc, (page % 2 == 0 ? 17 : 166) + 40, 90);
        });
        pause(2);
        shot("controls_hover_page");
        act(mc -> {
            int page = lastEntryPage(screen(mc));
            hover0(mc, (page % 2 == 0 ? 17 + 129 - 18 : 166 + 2) + 8, 179);
        });
        pause(2);
        shot("controls_hover_feather");
        hover(-40, -6);

        // Delete asks first, and typing takes the question back
        clickEntry(mc -> screen(mc).journal().entries.get(1).id, 20, 15);
        clickBook(268, 200, 0);
        check("the first Delete click only asks", s -> s.deleteArmed() && s.journal().entries.size() == 2);
        shot("controls_delete_asks");
        type("x");
        check("typing takes the question back", s -> !s.deleteArmed());
        clickBook(268, 200, 0);
        clickBook(268, 200, 0);
        pause(10);
        check("the second Delete click deletes the note", s -> s.journal().entries.size() == 1 && s.focusName().isEmpty());

        // a place can be deleted too, and its map file goes with it
        clickTab(Section.LOCATIONS);
        clickTab(Section.LOCATIONS);
        clickEntry(mc -> screen(mc).journal().entries.get(0).id, 20, 2);
        check("a click on a place's title writes in the title", s -> s.focusName().equals("title"));
        shot("controls_writing_place");
        clickBook(268, 200, 0);
        clickBook(268, 200, 0);
        pause(10);
        check("a place can be deleted", s -> s.journal().entries.isEmpty());
        act(mc -> {
            String id = JournalItem.id(mc.player.getMainHandItem());
            Path img = JournalStore.folder(mc.getSingleplayerServer(), id).resolve("img");
            try (Stream<Path> files = Files.list(img)) {
                Report.check("its map file is gone", files.findAny().isEmpty());
            } catch (IOException e) {
                Report.check("its map file is gone, it threw " + e, false);
            }
        });

        // a new note can be thrown away
        clickChoice(writeNoteRow());
        type("Throw away");
        clickBook(268, 200, 0);
        clickBook(268, 200, 0);
        pause(10);
        check("Delete throws away a new note", s -> s.journal().entries.isEmpty() && s.focusName().isEmpty());

        // an empty note is not saved
        clickChoice(writeNoteRow());
        clickBook(220, 200, 0);
        pause(10);
        check("Done on an empty new note saves nothing", s -> s.journal().entries.isEmpty());

        clickTab(Section.CONTENTS);
        check("the C tab still works in an empty book", s -> s.spread() == 0);
        shot("controls_empty_again");

        // a used book does not look new again when you delete entries
        addEntries(10, 0);
        check("ten notes wear the book", s -> s.journal().wear() == 1);
        act(mc -> {
            JournalScreen s = screen(mc);
            int page = s.pageOfEntry(s.journal().entries.get(0).id);
            while (s.spread() < page / 2) keyQuiet(mc, GLFW.GLFW_KEY_RIGHT);
        });
        pause(2);
        clickEntry(mc -> screen(mc).journal().entries.get(0).id, 20, 15);
        clickBook(268, 200, 0);
        clickBook(268, 200, 0);
        pause(10);
        check("deleting a note leaves nine", s -> s.journal().entries.size() == 9);
        check("the book stays worn after the delete", s -> s.journal().wear() == 1);
        finish();
    }

    private static void hover0(Minecraft mc, int dx, int dy) {
        try {
            double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            Field x = MouseHandler.class.getDeclaredField("xpos"), y = MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, (bookX(mc) + dx) * scale);
            y.setDouble(mc.mouseHandler, (bookY(mc) + dy) * scale);
        } catch (ReflectiveOperationException e) {
            Report.check("the script can move the mouse, it threw " + e, false);
        }
    }

    /** Turns a page with the arrow key, for moving around in a script. */
    private static void keyQuiet(Minecraft mc, int key) {
        mc.screen.keyPressed(key, 0, 0);
    }
}

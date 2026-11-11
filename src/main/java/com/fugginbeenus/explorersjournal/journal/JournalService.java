package com.fugginbeenus.explorersjournal.journal;

import com.fugginbeenus.explorersjournal.compat.LocationTooltipCompat;
import com.fugginbeenus.explorersjournal.config.EJConfig;
import com.fugginbeenus.explorersjournal.item.JournalItem;
import com.fugginbeenus.explorersjournal.map.MapDrawer;
import com.fugginbeenus.explorersjournal.map.MapJob;
import com.fugginbeenus.explorersjournal.net.EJNet;
import com.fugginbeenus.explorersjournal.net.EJPayloads;
import com.fugginbeenus.explorersjournal.registry.EJItems;
import com.fugginbeenus.explorersjournal.sketch.PixelCanvas;
import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the server does for a journal. The client only ever asks. */
public final class JournalService {
    private static final int MAX_PAGE = 99;
    private static final int MAX_SKETCH_BYTES = 4096, SKETCH_GAP_MS = 500, DRAWING_GAP_MS = 100;
    // how close you must be for your place's name to show in the wilderness, and how many other places a map shows
    private static final int NEAR = 32, MOST_MARKS = 6;
    private static final EJPayloads.PinInfo NO_PIN = new EJPayloads.PinInfo("", "", 0, 0, 0);
    private static final Map<UUID, EJPayloads.PinInfo> SENT_PINS = new HashMap<>();
    private static final Set<UUID> DRAWING = new HashSet<>();
    private static final Map<UUID, Long> LAST_SKETCH = new HashMap<>();

    private JournalService() {}

    public static void init() {
        EJNet.registerReceiver(EJPayloads.SAVE_ENTRY, JournalService::saveEntry);
        EJNet.registerReceiver(EJPayloads.DELETE_ENTRY, JournalService::deleteEntry);
        EJNet.registerReceiver(EJPayloads.PASTE_KEEPSAKE, JournalService::pasteKeepsake);
        EJNet.registerReceiver(EJPayloads.PEEL_KEEPSAKE, JournalService::peelKeepsake);
        EJNet.registerReceiver(EJPayloads.EDIT_KEEPSAKE, JournalService::editKeepsake);
        EJNet.registerReceiver(EJPayloads.SAVE_SKETCH, JournalService::saveSketch);
        EJNet.registerReceiver(EJPayloads.SAVE_DRAWING, JournalService::saveDrawing);
        EJNet.registerReceiver(EJPayloads.SET_BOOKMARK, JournalService::setBookmark);
        EJNet.registerReceiver(EJPayloads.MARK_PLACE, JournalService::markPlace);
        EJNet.registerReceiver(EJPayloads.REQUEST_MAP, JournalService::sendMap);
        EJNet.registerReceiver(EJPayloads.NAME_VILLAGE, JournalService::nameVillage);
        EJNet.registerReceiver(EJPayloads.SET_PIN, JournalService::setPin);
        if (LocationTooltipCompat.linked()) LocationTooltipCompat.addWildernessNames(JournalService::placeNear);
    }

    public static void open(ServerPlayer player, ItemStack stack) {
        MinecraftServer server = player.level().getServer();
        Journal journal = JournalStore.get(server, JournalItem.id(stack));
        if (journal == null) {
            journal = JournalStore.create(server, player.getUUID(), player.getGameProfile().getName());
        }
        if (!canWrite(player, journal) && !EJConfig.get().othersCanRead) {
            String owner = journal.ownerName.isBlank() ? "someone else" : journal.ownerName;
            player.displayClientMessage(Component.literal("This journal belongs to " + owner + ". On this server, journals are private."), true);
            return;
        }
        if (LocationTooltipCompat.linked() && lookAtPlaces(server, player, journal)) JournalStore.save(server, journal);
        JournalItem.store(stack, journal.id, journal.wear(), journal.entries.size(), journal.ownerName);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        EJNet.send(player, EJPayloads.OPEN_JOURNAL, message(player, journal));
        sendPin(player);
    }

    /** The journal as this player gets it, with the rules that change what the book offers. */
    private static EJPayloads.OpenJournal message(ServerPlayer player, Journal journal) {
        EJConfig cfg = EJConfig.get();
        return new EJPayloads.OpenJournal(journal.toNet(), canWrite(player, journal), journal.wear(), cfg.pasteCostsPaper, cfg.maxEntries);
    }

    private static boolean full(Journal journal) {
        return journal.entries.size() >= EJConfig.get().maxEntries;
    }

    /** Places get renamed and villages get named while the book is shut. Returns true if anything changed. */
    private static boolean lookAtPlaces(MinecraftServer server, ServerPlayer player, Journal journal) {
        boolean owner = canWrite(player, journal), changed = false;
        for (Entry entry : journal.entries) {
            if (entry.place == null) continue;
            String now = LocationTooltipCompat.nameOf(server, entry.place);
            String renamed = now == null || now.equals(entry.placeName) ? null : now;
            boolean nameable = owner ? LocationTooltipCompat.canNameVillage(player, entry.place) : entry.nameable;
            if (!java.util.Objects.equals(renamed, entry.renamed) || nameable != entry.nameable) {
                entry.renamed = renamed;
                entry.nameable = nameable;
                changed = true;
            }
        }
        return changed;
    }

    /** Names the village a place stands in, for everyone. Location Tooltip's own rules decide. The place takes the new name, so it is not crossed out. */
    private static void nameVillage(MinecraftServer server, ServerPlayer player, EJPayloads.NameVillage msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        Entry entry = journal.entry(msg.entryId());
        if (entry == null || entry.place == null) return;
        String name = clean(msg.name(), EJPayloads.MAX_TITLE).replace('\n', ' ').strip();
        if (name.isEmpty() || !LocationTooltipCompat.nameVillage(player, entry.place, name)) return;
        String now = LocationTooltipCompat.nameOf(server, entry.place);
        entry.title = now != null ? clip(now, EJPayloads.MAX_TITLE) : name;
        entry.placeName = now != null ? now : name;
        entry.renamed = null;
        entry.nameable = LocationTooltipCompat.canNameVillage(player, entry.place);
        commit(server, player, stack, journal);
    }

    private static void setPin(MinecraftServer server, ServerPlayer player, EJPayloads.SetPin msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        Entry entry = msg.entryId().isEmpty() ? null : journal.entry(msg.entryId());
        if (!msg.entryId().isEmpty() && (entry == null || !entry.isLocation())) return;
        journal.pinned = entry == null ? "" : entry.id;
        commit(server, player, stack, journal);
        sendPin(player);
    }

    /** The journals this player carries and wrote. */
    private static List<Journal> ownJournals(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<Journal> out = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(EJItems.JOURNAL) || JournalItem.id(stack) == null) continue;
            Journal journal = JournalStore.get(server, JournalItem.id(stack));
            if (journal != null && canWrite(player, journal) && !out.contains(journal)) out.add(journal);
        }
        return out;
    }

    /** Tells the client which place to show on Location Tooltip's pill: the pin in the first journal you carry that has one. Only a change goes out. */
    public static void sendPin(ServerPlayer player) {
        EJPayloads.PinInfo pin = NO_PIN;
        for (Journal journal : ownJournals(player)) {
            Entry entry = journal.pinned.isEmpty() ? null : journal.entry(journal.pinned);
            if (entry != null && entry.isLocation()) {
                String name = entry.renamedSince() ? entry.renamed : entry.title;
                pin = new EJPayloads.PinInfo(name.isBlank() ? "Pinned place" : name, entry.dim, entry.x, entry.y, entry.z);
                break;
            }
        }
        if (!pin.equals(SENT_PINS.put(player.getUUID(), pin))) EJNet.send(player, EJPayloads.PIN_INFO, pin);
    }

    /** A player came or went, so the next pin goes out whatever it is. */
    public static void forgetPin(ServerPlayer player) {
        SENT_PINS.remove(player.getUUID());
    }

    /** The pill follows the journal: put it in a chest and the pill goes, pick it up and it comes back. */
    public static void tickPins(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendPin(player);
    }

    /** The nearest wilderness place you marked and named yourself, in a journal you carry, within a short walk. Location Tooltip shows it, for you alone. */
    private static String placeNear(ServerPlayer player, BlockPos pos) {
        String dim = player.level().dimension().location().toString();
        Entry best = null;
        double bestDistance = NEAR * NEAR;
        for (Journal journal : ownJournals(player)) {
            for (Entry entry : journal.entries) {
                if (!entry.isLocation() || !dim.equals(entry.dim) || entry.place != null || entry.title.isBlank() || entry.title.equals(entry.placeName)) continue;
                double dx = entry.x - pos.getX(), dz = entry.z - pos.getZ(), d = dx * dx + dz * dz;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = entry;
                }
            }
        }
        return best == null ? null : best.title;
    }

    public static boolean canWrite(ServerPlayer player, Journal journal) {
        return journal.owner.equals(player.getUUID().toString());
    }

    /** The journal this player has in hand, if it is the one they asked for. */
    public static ItemStack held(ServerPlayer player, String journalId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(EJItems.JOURNAL) && journalId.equals(JournalItem.id(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void saveEntry(MinecraftServer server, ServerPlayer player, EJPayloads.SaveEntry msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;

        String title = clean(msg.title(), EJPayloads.MAX_TITLE).replace('\n', ' ');
        String text = clean(msg.text(), EJPayloads.MAX_TEXT);
        boolean empty = title.isBlank() && text.isBlank();
        Entry entry = journal.entry(msg.entryId());
        if (entry == null) {
            if (full(journal) || empty || !isUuid(msg.entryId())) return;
            entry = new Entry();
            entry.id = msg.entryId();
            entry.day = day(player);
            journal.entries.add(entry);
        } else if (empty && entry.keepsakes.isEmpty() && entry.map == null && !entry.isLocation()) {
            // a note with nothing left in it is gone
            journal.entries.remove(entry);
            if (entry.id.equals(journal.bookmark)) journal.bookmark = "";
            commit(server, player, stack, journal);
            return;
        }
        entry.title = title;
        entry.text = text;
        commit(server, player, stack, journal);
    }

    private static void deleteEntry(MinecraftServer server, ServerPlayer player, EJPayloads.DeleteEntry msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        Entry entry = journal.entry(msg.entryId());
        if (entry == null) return;
        removeEntry(server, player, stack, journal, entry);
    }

    /** Takes an entry out. Its picture file goes, and what was taped in comes back to this player. */
    public static void removeEntry(MinecraftServer server, ServerPlayer player, ItemStack stack, Journal journal, Entry entry) {
        journal.entries.remove(entry);
        if (entry.id.equals(journal.bookmark)) journal.bookmark = "";
        boolean wasPinned = entry.id.equals(journal.pinned);
        if (wasPinned) journal.pinned = "";
        if (entry.map != null && journal.entries.stream().noneMatch(other -> entry.map.equals(other.map))) {
            JournalStore.deleteMap(server, journal.id, entry.map);
        }
        // what was taped in comes back to you
        for (Keepsake keepsake : entry.keepsakes) {
            ItemStack back = Keepsakes.read(keepsake.item, server.registryAccess());
            if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
            JournalStore.saveWithWorld(journal);
        }
        commit(server, player, stack, journal);
        if (wasPinned) sendPin(player);
    }

    /** A client drew this, so the server checks that it really is a sketch, and how often they come. */
    private static void saveSketch(MinecraftServer server, ServerPlayer player, EJPayloads.SaveImage msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        if (!isUuid(msg.entryId()) || !isUuid(msg.imageId()) || journal.entry(msg.entryId()) != null || full(journal)) return;
        if (msg.image().length > MAX_SKETCH_BYTES || SketchDrawer.unpack(msg.image()) == null) return;
        long now = Util.getMillis();
        Long last = LAST_SKETCH.get(player.getUUID());
        if (last != null && now - last < SKETCH_GAP_MS) return;
        LAST_SKETCH.put(player.getUUID(), now);
        if (!JournalStore.saveMap(server, journal.id, msg.imageId(), msg.image())) return;

        Entry entry = new Entry();
        entry.id = msg.entryId();
        entry.type = Entry.SKETCH;
        entry.day = day(player);
        entry.title = clean(msg.title(), EJPayloads.MAX_TITLE).replace('\n', ' ');
        entry.text = clean(msg.text(), EJPayloads.MAX_TEXT);
        entry.map = msg.imageId();
        journal.entries.add(entry);
        commit(server, player, stack, journal);
    }

    /** A new drawing, or a new picture for one you already have. Each picture has its own file, so nobody keeps seeing the old one. */
    private static void saveDrawing(MinecraftServer server, ServerPlayer player, EJPayloads.SaveImage msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        if (!isUuid(msg.entryId()) || !isUuid(msg.imageId())) return;
        Entry entry = journal.entry(msg.entryId());
        if (entry == null ? full(journal) : !entry.isDrawing()) return;
        if (msg.image().length > MAX_SKETCH_BYTES || PixelCanvas.unpack(msg.image()) == null) return;
        long now = Util.getMillis();
        Long last = LAST_SKETCH.get(player.getUUID());
        if (last != null && now - last < DRAWING_GAP_MS) return;
        LAST_SKETCH.put(player.getUUID(), now);
        if (!JournalStore.saveMap(server, journal.id, msg.imageId(), msg.image())) return;

        if (entry == null) {
            entry = new Entry();
            entry.id = msg.entryId();
            entry.type = Entry.DRAWING;
            entry.day = day(player);
            journal.entries.add(entry);
        }
        String old = entry.map;
        entry.title = clean(msg.title(), EJPayloads.MAX_TITLE).replace('\n', ' ');
        entry.text = clean(msg.text(), EJPayloads.MAX_TEXT);
        entry.map = msg.imageId();
        if (old != null && !old.equals(entry.map)) JournalStore.deleteMap(server, journal.id, old);
        commit(server, player, stack, journal);
    }

    /** The only way an item goes in: one item leaves the slot, in one step on the server thread. */
    private static void pasteKeepsake(MinecraftServer server, ServerPlayer player, EJPayloads.PasteKeepsake msg) {
        ItemStack journalStack = held(player, msg.journalId());
        if (journalStack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal) || !isUuid(msg.keepsakeId())) return;

        boolean mainSlot = msg.slot() >= 0 && msg.slot() < Inventory.INVENTORY_SIZE;
        if (!mainSlot && msg.slot() != Inventory.SLOT_OFFHAND) return;
        ItemStack stack = player.getInventory().getItem(msg.slot());
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(msg.itemKey())) return;
        if (!Keepsakes.canPaste(stack, server.registryAccess())) return;

        String data = Keepsakes.write(stack.copyWithCount(1), server.registryAccess());
        if (journal.toNet().length() + data.length() > EJPayloads.MAX_JSON * 7 / 10) return;

        Entry entry = journal.entry(msg.entryId());
        if (entry == null) {
            if (full(journal) || !isUuid(msg.entryId())) return;
            entry = new Entry();
            entry.id = msg.entryId();
            entry.type = Entry.SCRAPBOOK.equals(msg.entryType()) || Entry.DRAWING.equals(msg.entryType()) ? msg.entryType() : Entry.NOTE;
            entry.day = day(player);
            journal.entries.add(entry);
        }

        int paper = -1;
        if (EJConfig.get().pasteCostsPaper) {
            paper = paperSlot(player.getInventory(), msg.slot());
            if (paper < 0) return;
        }
        stack.shrink(1);
        if (paper >= 0) player.getInventory().getItem(paper).shrink(1);
        player.getInventory().setChanged();

        Keepsake keepsake = new Keepsake();
        keepsake.id = msg.keepsakeId();
        keepsake.item = data;
        place(keepsake, msg.page(), msg.x(), msg.y());
        entry.keepsakes.add(keepsake);
        JournalStore.saveWithWorld(journal);
        commit(server, player, journalStack, journal);
    }

    /** A slot with paper to cut the tape from, not counting a paper that is being taped in. -1 if there is none. */
    private static int paperSlot(Inventory inventory, int pastedSlot) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.PAPER) && stack.getCount() > (slot == pastedSlot ? 1 : 0)) return slot;
        }
        return -1;
    }

    private static void place(Keepsake keepsake, int page, int x, int y) {
        keepsake.page = Mth.clamp(page, 0, MAX_PAGE);
        keepsake.x = Mth.clamp(x, -8, 129);
        keepsake.y = Mth.clamp(y, -8, 172);
    }

    /** Moves a thing on its pages, and writes the note beside it. The item itself stays where it is, in the journal. */
    private static void editKeepsake(MinecraftServer server, ServerPlayer player, EJPayloads.EditKeepsake msg) {
        ItemStack journalStack = held(player, msg.journalId());
        if (journalStack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        Entry entry = journal.entry(msg.entryId());
        Keepsake keepsake = entry == null ? null : entry.keepsake(msg.keepsakeId());
        if (keepsake == null) return;
        String note = clean(msg.note(), EJPayloads.MAX_NOTE).replace('\n', ' ').strip();
        int page = keepsake.page, x = keepsake.x, y = keepsake.y;
        String was = keepsake.note == null ? "" : keepsake.note;
        place(keepsake, msg.page(), msg.x(), msg.y());
        keepsake.note = note.isEmpty() ? null : note;
        if (page == keepsake.page && x == keepsake.x && y == keepsake.y && was.equals(note)) return;
        commit(server, player, journalStack, journal);
    }

    /** The only way an item comes out. If it cannot be read, its mod is gone, so it stays until the mod comes back. */
    private static void peelKeepsake(MinecraftServer server, ServerPlayer player, EJPayloads.PeelKeepsake msg) {
        ItemStack journalStack = held(player, msg.journalId());
        if (journalStack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        Entry entry = journal.entry(msg.entryId());
        Keepsake keepsake = entry == null ? null : entry.keepsake(msg.keepsakeId());
        if (keepsake == null) return;
        ItemStack back = Keepsakes.read(keepsake.item, server.registryAccess());
        if (back.isEmpty()) return;

        entry.keepsakes.remove(keepsake);
        player.getInventory().placeItemBackInInventory(back);
        if (entry.isEmpty()) {
            journal.entries.remove(entry);
            if (entry.id.equals(journal.bookmark)) journal.bookmark = "";
        }
        JournalStore.saveWithWorld(journal);
        commit(server, player, journalStack, journal);
    }

    private static void setBookmark(MinecraftServer server, ServerPlayer player, EJPayloads.SetBookmark msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal)) return;
        if (!msg.entryId().isEmpty() && journal.entry(msg.entryId()) == null) return;
        journal.bookmark = msg.entryId();
        commit(server, player, stack, journal);
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void markPlace(MinecraftServer server, ServerPlayer player, EJPayloads.MarkPlace msg) {
        ItemStack stack = held(player, msg.journalId());
        if (stack.isEmpty() || !DRAWING.add(player.getUUID())) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null || !canWrite(player, journal) || full(journal)) {
            DRAWING.remove(player.getUUID());
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        String title = clip(PlaceNames.at(level, pos), EJPayloads.MAX_TITLE);
        // with Location Tooltip, a place has the name it has there: your own region, a village, a structure
        LocationTooltipCompat.Place place = LocationTooltipCompat.placeAt(server, level.dimension().location(), pos);
        if (place != null) title = clip(place.name(), EJPayloads.MAX_TITLE);
        List<MapMark> marks = marksAround(server, level, pos);
        boolean nameable = place != null && LocationTooltipCompat.canNameVillage(player, place.id());
        long day = day(player);
        String dim = level.dimension().location().toString();
        UUID playerId = player.getUUID();
        String finalTitle = title;

        MapJob.start(level, pos, packed -> {
            DRAWING.remove(playerId);
            String mapId = UUID.randomUUID().toString();
            if (!JournalStore.saveMap(server, journal.id, mapId, packed)) return;
            Entry entry = new Entry();
            entry.id = UUID.randomUUID().toString();
            entry.type = Entry.LOCATION;
            entry.day = day;
            entry.title = finalTitle;
            entry.dim = dim;
            entry.x = pos.getX();
            entry.y = pos.getY();
            entry.z = pos.getZ();
            entry.map = mapId;
            entry.placeName = place != null ? place.name() : finalTitle;
            if (place != null) {
                entry.place = place.id();
                entry.marker = place.marker();
                entry.nameable = nameable;
            }
            entry.marks = marks.isEmpty() ? null : marks;
            journal.entries.add(entry);

            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online == null) {
                JournalStore.save(server, journal);
                return;
            }
            commit(server, online, held(online, journal.id), journal);
        });
    }

    /** The other places on a new map, from Location Tooltip: those whose middle is on the map and that do not hold the marked spot, nearest the middle first. */
    private static List<MapMark> marksAround(MinecraftServer server, ServerLevel level, BlockPos pos) {
        List<MapMark> marks = new ArrayList<>();
        if (!LocationTooltipCompat.linked()) return marks;
        int x0 = pos.getX() - MapDrawer.W, z0 = pos.getZ() - MapDrawer.H;
        for (LocationTooltipCompat.Place p : LocationTooltipCompat.placesWithin(server, level.dimension().location(), x0, z0, x0 + MapDrawer.BLOCKS_W - 1, z0 + MapDrawer.BLOCKS_H - 1)) {
            boolean holdsSpot = pos.getX() >= p.minX() && pos.getX() <= p.maxX() && pos.getZ() >= p.minZ() && pos.getZ() <= p.maxZ();
            int mx = ((p.minX() + p.maxX()) / 2 - x0) / 2, my = ((p.minZ() + p.maxZ()) / 2 - z0) / 2;
            if (holdsSpot || mx < 4 || my < 4 || mx > MapDrawer.W - 5 || my > MapDrawer.H - 5) continue;
            MapMark mark = new MapMark();
            mark.name = clip(p.name(), 32);
            mark.marker = p.marker();
            mark.x = mx;
            mark.y = my;
            marks.add(mark);
        }
        marks.sort(java.util.Comparator.comparingInt(m -> (m.x - MapDrawer.W / 2) * (m.x - MapDrawer.W / 2) + (m.y - MapDrawer.H / 2) * (m.y - MapDrawer.H / 2)));
        return marks.size() > MOST_MARKS ? new ArrayList<>(marks.subList(0, MOST_MARKS)) : marks;
    }

    private static void sendMap(MinecraftServer server, ServerPlayer player, EJPayloads.RequestMap msg) {
        if (held(player, msg.journalId()).isEmpty()) return;
        Journal journal = JournalStore.get(server, msg.journalId());
        if (journal == null) return;
        for (Entry entry : journal.entries) {
            if (msg.mapId().equals(entry.map)) {
                byte[] packed = JournalStore.loadMap(server, journal.id, entry.map);
                if (packed != null) EJNet.send(player, EJPayloads.MAP_IMAGE, new EJPayloads.MapImage(entry.map, packed));
                return;
            }
        }
    }

    public static void commit(MinecraftServer server, ServerPlayer player, ItemStack stack, Journal journal) {
        journal.mostEntries = Math.max(journal.mostEntries, journal.entries.size());
        JournalStore.save(server, journal);
        if (!stack.isEmpty()) JournalItem.store(stack, journal.id, journal.wear(), journal.entries.size(), journal.ownerName);
        EJNet.send(player, EJPayloads.JOURNAL_DATA, message(player, journal));
    }

    public static void clear() {
        DRAWING.clear();
        LAST_SKETCH.clear();
        SENT_PINS.clear();
    }

    public static long day(ServerPlayer player) {
        return player.level().getDayTime() / 24000L + 1;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** No formatting codes or control characters from a client. */
    private static String clean(String s, int max) {
        StringBuilder out = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && out.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '\n' || (c >= 32 && c != 127 && c != 167)) out.append(c);
        }
        return out.toString();
    }
}

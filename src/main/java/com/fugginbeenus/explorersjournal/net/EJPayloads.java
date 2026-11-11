package com.fugginbeenus.explorersjournal.net;

import com.fugginbeenus.explorersjournal.util.EJId;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class EJPayloads {
    private EJPayloads() {}

    public record Def<T>(ResourceLocation id, BiConsumer<T, FriendlyByteBuf> writer, Function<FriendlyByteBuf, T> reader) {}

    public static final int MAX_JSON = 1_000_000;
    public static final int MAX_TITLE = 40;
    public static final int MAX_TEXT = 4000;
    public static final int MAX_NOTE = 80;

    /** A journal, with what this player may do in it and the server's rules for it. */
    public record OpenJournal(String json, boolean canWrite, int wear, boolean paperCost, int maxEntries) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_JSON);
            buf.writeBoolean(canWrite);
            buf.writeVarInt(wear);
            buf.writeBoolean(paperCost);
            buf.writeVarInt(maxEntries);
        }
        static OpenJournal read(FriendlyByteBuf buf) {
            return new OpenJournal(buf.readUtf(MAX_JSON), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
        }
    }
    public static final Def<OpenJournal> OPEN_JOURNAL = new Def<>(EJId.of("open_journal"), OpenJournal::write, OpenJournal::read);
    public static final Def<OpenJournal> JOURNAL_DATA = new Def<>(EJId.of("journal_data"), OpenJournal::write, OpenJournal::read);

    /** The client picks the ID of a new note, so its page keeps the same look and spot when the save comes back. */
    public record SaveEntry(String journalId, String entryId, String title, String text) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
            buf.writeUtf(title, MAX_TITLE * 4);
            buf.writeUtf(text, MAX_TEXT * 4);
        }
        static SaveEntry read(FriendlyByteBuf buf) {
            return new SaveEntry(buf.readUtf(64), buf.readUtf(64), buf.readUtf(MAX_TITLE * 4), buf.readUtf(MAX_TEXT * 4));
        }
    }
    public static final Def<SaveEntry> SAVE_ENTRY = new Def<>(EJId.of("save_entry"), SaveEntry::write, SaveEntry::read);

    public record DeleteEntry(String journalId, String entryId) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
        }
        static DeleteEntry read(FriendlyByteBuf buf) {
            return new DeleteEntry(buf.readUtf(64), buf.readUtf(64));
        }
    }
    public static final Def<DeleteEntry> DELETE_ENTRY = new Def<>(EJId.of("delete_entry"), DeleteEntry::write, DeleteEntry::read);

    /** The client only names the slot and what it thinks is there. The server checks and moves the item. A new note's entry is made here if the entry does not exist yet. */
    public record PasteKeepsake(String journalId, String entryId, String entryType, String keepsakeId, int slot, String itemKey, int page, int x, int y) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
            buf.writeUtf(entryType, 32);
            buf.writeUtf(keepsakeId, 64);
            buf.writeVarInt(slot);
            buf.writeUtf(itemKey, 256);
            buf.writeVarInt(page);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
        }
        static PasteKeepsake read(FriendlyByteBuf buf) {
            return new PasteKeepsake(buf.readUtf(64), buf.readUtf(64), buf.readUtf(32), buf.readUtf(64), buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }
    public static final Def<PasteKeepsake> PASTE_KEEPSAKE = new Def<>(EJId.of("paste_keepsake"), PasteKeepsake::write, PasteKeepsake::read);

    /** A finished sketch. The client draws it, and the server checks it before it keeps it. */
    public record SaveImage(String journalId, String entryId, String imageId, String title, String text, byte[] image) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
            buf.writeUtf(imageId, 64);
            buf.writeUtf(title, MAX_TITLE * 4);
            buf.writeUtf(text, MAX_TEXT * 4);
            buf.writeByteArray(image);
        }
        static SaveImage read(FriendlyByteBuf buf) {
            return new SaveImage(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readUtf(MAX_TITLE * 4), buf.readUtf(MAX_TEXT * 4), buf.readByteArray());
        }
    }
    public static final Def<SaveImage> SAVE_SKETCH = new Def<>(EJId.of("save_sketch"), SaveImage::write, SaveImage::read);
    public static final Def<SaveImage> SAVE_DRAWING = new Def<>(EJId.of("save_drawing"), SaveImage::write, SaveImage::read);

    /** Moves a thing on its entry's pages and sets the note beside it. No item moves, so it is a plain save. */
    public record EditKeepsake(String journalId, String entryId, String keepsakeId, int page, int x, int y, String note) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
            buf.writeUtf(keepsakeId, 64);
            buf.writeVarInt(page);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeUtf(note, MAX_NOTE * 4);
        }
        static EditKeepsake read(FriendlyByteBuf buf) {
            return new EditKeepsake(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_NOTE * 4));
        }
    }
    public static final Def<EditKeepsake> EDIT_KEEPSAKE = new Def<>(EJId.of("edit_keepsake"), EditKeepsake::write, EditKeepsake::read);

    public record PeelKeepsake(String journalId, String entryId, String keepsakeId) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
            buf.writeUtf(keepsakeId, 64);
        }
        static PeelKeepsake read(FriendlyByteBuf buf) {
            return new PeelKeepsake(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64));
        }
    }
    public static final Def<PeelKeepsake> PEEL_KEEPSAKE = new Def<>(EJId.of("peel_keepsake"), PeelKeepsake::write, PeelKeepsake::read);

    /** entryId is empty to take the bookmark out. */
    public record SetBookmark(String journalId, String entryId) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
        }
        static SetBookmark read(FriendlyByteBuf buf) {
            return new SetBookmark(buf.readUtf(64), buf.readUtf(64));
        }
    }
    public static final Def<SetBookmark> SET_BOOKMARK = new Def<>(EJId.of("set_bookmark"), SetBookmark::write, SetBookmark::read);

    /** Asks Location Tooltip to give the village of this place a name, for everyone. */
    public record NameVillage(String journalId, String entryId, String name) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
            buf.writeUtf(name, MAX_TITLE * 4);
        }
        static NameVillage read(FriendlyByteBuf buf) {
            return new NameVillage(buf.readUtf(64), buf.readUtf(64), buf.readUtf(MAX_TITLE * 4));
        }
    }
    public static final Def<NameVillage> NAME_VILLAGE = new Def<>(EJId.of("name_village"), NameVillage::write, NameVillage::read);

    /** entryId is empty to take the pin out. */
    public record SetPin(String journalId, String entryId) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(entryId, 64);
        }
        static SetPin read(FriendlyByteBuf buf) {
            return new SetPin(buf.readUtf(64), buf.readUtf(64));
        }
    }
    public static final Def<SetPin> SET_PIN = new Def<>(EJId.of("set_pin"), SetPin::write, SetPin::read);

    /** The place pinned in a journal you carry, for Location Tooltip's pill. An empty name means none. */
    public record PinInfo(String name, String dim, int x, int y, int z) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(name, MAX_TITLE * 4);
            buf.writeUtf(dim, 256);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeVarInt(z);
        }
        static PinInfo read(FriendlyByteBuf buf) {
            return new PinInfo(buf.readUtf(MAX_TITLE * 4), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }
    public static final Def<PinInfo> PIN_INFO = new Def<>(EJId.of("pin_info"), PinInfo::write, PinInfo::read);

    public record MarkPlace(String journalId) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
        }
        static MarkPlace read(FriendlyByteBuf buf) {
            return new MarkPlace(buf.readUtf(64));
        }
    }
    public static final Def<MarkPlace> MARK_PLACE = new Def<>(EJId.of("mark_place"), MarkPlace::write, MarkPlace::read);

    public record RequestMap(String journalId, String mapId) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(journalId, 64);
            buf.writeUtf(mapId, 64);
        }
        static RequestMap read(FriendlyByteBuf buf) {
            return new RequestMap(buf.readUtf(64), buf.readUtf(64));
        }
    }
    public static final Def<RequestMap> REQUEST_MAP = new Def<>(EJId.of("request_map"), RequestMap::write, RequestMap::read);

    public record MapImage(String mapId, byte[] packed) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(mapId, 64);
            buf.writeByteArray(packed);
        }
        static MapImage read(FriendlyByteBuf buf) {
            return new MapImage(buf.readUtf(64), buf.readByteArray());
        }
    }
    public static final Def<MapImage> MAP_IMAGE = new Def<>(EJId.of("map_image"), MapImage::write, MapImage::read);
}

package com.fugginbeenus.explorersjournal.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** The only class that talks to Location Tooltip. It asks by name, so each mod runs alone. With no Location Tooltip, or one too old for the link, every call does nothing. */
public final class LocationTooltipCompat {
    private static final Logger LOG = LoggerFactory.getLogger("explorersjournal");
    private static final String API = "com.fugginbeenus.locationtooltip.api.LocationTooltipApi";
    private static boolean tried, ready;
    private static Method placeAt, placesWithin, nameOf, canNameVillage, nameVillage, addWildernessNames, addPill;

    /** A place Location Tooltip knows: your own region, a village, a structure. Its ground runs from min to max. */
    public record Place(String id, String name, String marker, int minX, int minZ, int maxX, int maxZ) {}

    private LocationTooltipCompat() {}

    public static boolean linked() {
        if (tried) return ready;
        tried = true;
        if (!FabricLoader.getInstance().isModLoaded("locationtooltip")) return false;
        try {
            Class<?> api = Class.forName(API);
            placeAt = api.getMethod("placeAt", MinecraftServer.class, ResourceLocation.class, BlockPos.class);
            placesWithin = api.getMethod("placesWithin", MinecraftServer.class, ResourceLocation.class, int.class, int.class, int.class, int.class);
            nameOf = api.getMethod("nameOf", MinecraftServer.class, String.class);
            canNameVillage = api.getMethod("canNameVillage", ServerPlayer.class, String.class);
            nameVillage = api.getMethod("nameVillage", ServerPlayer.class, String.class, String.class);
            addWildernessNames = api.getMethod("addWildernessNames", BiFunction.class);
            addPill = api.getMethod("addPill", Supplier.class, Supplier.class);
            ready = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.info("This Location Tooltip is too old to link with the journal. Each mod still works alone.");
        }
        return ready;
    }

    private static Object call(Method method, Object... args) {
        if (!linked()) return null;
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.warn("Location Tooltip did not answer the journal, so the link is off.", e);
            ready = false;
            return null;
        }
    }

    /** The smallest place that holds this spot, or null. */
    public static Place placeAt(MinecraftServer server, ResourceLocation dim, BlockPos pos) {
        return place((String[]) call(placeAt, server, dim, pos));
    }

    /** Every place whose ground crosses this box, smallest first. */
    @SuppressWarnings("unchecked")
    public static List<Place> placesWithin(MinecraftServer server, ResourceLocation dim, int minX, int minZ, int maxX, int maxZ) {
        List<Place> out = new ArrayList<>();
        Object raw = call(placesWithin, server, dim, minX, minZ, maxX, maxZ);
        if (raw instanceof List<?> list) {
            for (Object row : list) {
                Place place = row instanceof String[] parts ? place(parts) : null;
                if (place != null) out.add(place);
            }
        }
        return out;
    }

    /** A place's name now, or null if it is gone. */
    public static String nameOf(MinecraftServer server, String id) {
        return call(nameOf, server, id) instanceof String name ? name : null;
    }

    public static boolean canNameVillage(ServerPlayer player, String id) {
        return Boolean.TRUE.equals(call(canNameVillage, player, id));
    }

    /** Location Tooltip's own rules decide, and it tells the player how it went. */
    public static boolean nameVillage(ServerPlayer player, String id, String name) {
        return Boolean.TRUE.equals(call(nameVillage, player, id, name));
    }

    public static void addWildernessNames(BiFunction<ServerPlayer, BlockPos, String> names) {
        call(addWildernessNames, names);
    }

    public static void addPill(Supplier<String> label, Supplier<String> icon) {
        call(addPill, label, icon);
    }

    private static Place place(String[] parts) {
        if (parts == null || parts.length < 7) return null;
        try {
            return new Place(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

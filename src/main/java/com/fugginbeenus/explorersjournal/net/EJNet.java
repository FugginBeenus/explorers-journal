package com.fugginbeenus.explorersjournal.net;

import com.fugginbeenus.explorersjournal.util.EJId;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class EJNet {
    private EJNet() {}

    @FunctionalInterface
    public interface ServerReceiver<T> {
        void receive(MinecraftServer server, ServerPlayer player, T payload);
    }

    //? if >=1.21 {
    /*private static final java.util.Map<net.minecraft.resources.ResourceLocation, EJPayloads.Def<?>> DEFS = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, ServerReceiver<?>> HANDLERS = new java.util.HashMap<>();

    public record Carrier(net.minecraft.resources.ResourceLocation channel, byte[] data) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Carrier> ID =
                new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(EJId.of("carrier"));
        public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, Carrier> CODEC =
                net.minecraft.network.codec.StreamCodec.of(
                        (buf, value) -> { buf.writeResourceLocation(value.channel()); buf.writeByteArray(value.data()); },
                        buf -> new Carrier(buf.readResourceLocation(), buf.readByteArray()));
        public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() { return ID; }
    }

    public static void init() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(Carrier.ID, Carrier.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(Carrier.ID, Carrier.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Carrier.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            dispatch(player.level().getServer(), player, payload);
        });
    }

    public static <T> void registerReceiver(EJPayloads.Def<T> def, ServerReceiver<T> handler) {
        DEFS.put(def.id(), def);
        HANDLERS.put(def.id(), handler);
    }

    @SuppressWarnings("unchecked")
    private static void dispatch(MinecraftServer server, ServerPlayer player, Carrier carrier) {
        EJPayloads.Def<Object> def = (EJPayloads.Def<Object>) DEFS.get(carrier.channel());
        ServerReceiver<Object> handler = (ServerReceiver<Object>) HANDLERS.get(carrier.channel());
        if (def == null || handler == null) return;
        Object value = def.reader().apply(new FriendlyByteBuf(Unpooled.wrappedBuffer(carrier.data())));
        server.execute(() -> handler.receive(server, player, value));
    }

    public static <T> void send(ServerPlayer player, EJPayloads.Def<T> def, T payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        def.writer().accept(payload, buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        ServerPlayNetworking.send(player, new Carrier(def.id(), data));
    }
    *///?} else {
    public static void init() {}

    public static <T> void registerReceiver(EJPayloads.Def<T> def, ServerReceiver<T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(def.id(), (server, player, netHandler, buf, responseSender) -> {
            T payload = def.reader().apply(buf);
            server.execute(() -> handler.receive(server, player, payload));
        });
    }

    public static <T> void send(ServerPlayer player, EJPayloads.Def<T> def, T payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        def.writer().accept(payload, buf);
        ServerPlayNetworking.send(player, def.id(), buf);
    }
    //?}
}

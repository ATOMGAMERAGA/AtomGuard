package com.atomguard.velocity.module.limbo;

import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.reflect.Method;

/**
 * Limbo doğrulama için gereken Minecraft paketlerini Velocity oyuncusuna gönderir.
 *
 * Kullanılan paketler (1.21.4, protokol 769):
 *   0x40 — Synchronize Player Position (teleport)
 *
 * NOT: Bu sınıf Velocity iç API'sine reflection ile erişir.
 * Velocity 3.3.x ile test edilmiştir.
 */
public class LimboPacketHelper {

    /**
     * Oyuncuyu belirtilen konuma teleport et (physics challenge başlangıcı).
     *
     * SynchronizePlayerPosition paketi (0x40 in 1.21.4):
     *   teleport_id (VarInt), x (Double), y (Double), z (Double),
     *   vx (Double), vy (Double), vz (Double),
     *   adyaw (Float), adpitch (Float), flags (Int)
     */
    public static boolean sendTeleport(Player player, double x, double y, double z, int teleportId) {
        try {
            ByteBuf buf = Unpooled.buffer();
            writeVarInt(buf, 0x40);
            writeVarInt(buf, teleportId);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeDouble(0.0);
            buf.writeDouble(0.0);
            buf.writeDouble(0.0);
            buf.writeFloat(0.0f);
            buf.writeFloat(0.0f);
            buf.writeInt(0);

            sendRawPacket(player, buf);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reflection ile ConnectedPlayer.getConnection().write(buf) çağırır.
     * Velocity internal API — Velocity 3.3.x ile çalışır.
     */
    private static void sendRawPacket(Player player, ByteBuf buf) throws Exception {
        Object connection = player.getClass().getMethod("getConnection").invoke(player);
        Method write = connection.getClass().getMethod("write", Object.class);
        write.invoke(connection, buf);
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}

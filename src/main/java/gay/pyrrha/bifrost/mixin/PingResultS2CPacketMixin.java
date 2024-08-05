package gay.pyrrha.bifrost.mixin;

import gay.pyrrha.bifrost.Bifrost;
import gay.pyrrha.bifrost.PingResultS2CPacketInject;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPingResultPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(PingResultS2CPacket.class)
public abstract class PingResultS2CPacketMixin implements PingResultS2CPacketInject {
    @Unique public byte[] bifrost$publicKey;

    @Inject(method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V", at = @At("TAIL"))
    private void ctor(PacketByteBuf buf, CallbackInfo ci) {
        System.out.println("readable bytes: " + buf.readableBytes());
        try {
            bifrost$publicKey = buf.readByteArray();
        } catch (IndexOutOfBoundsException e) {
            System.out.println("ping didn't contain public key, ignoring");
        } catch (Exception e) {
            System.out.println("unknown exception: " + e.getMessage());
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void write(PacketByteBuf buf, CallbackInfo ci) {
        buf.writeByteArray(Bifrost.INSTANCE.getServerKeyPair().component1().getEncoded());
    }

    @Nullable
    @Override
    public byte[] bifrost$getEncodedPublicKey() {
        return this.bifrost$publicKey;
    }
}

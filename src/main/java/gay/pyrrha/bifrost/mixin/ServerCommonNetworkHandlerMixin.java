package gay.pyrrha.bifrost.mixin;

import gay.pyrrha.bifrost.ServerCookieEvents;
import net.minecraft.network.packet.c2s.common.CookieResponseC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {
    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow @Final private boolean transferred;

    @Inject(method = "onCookieResponse", at = @At("HEAD"), cancellable = true)
    private void onCookieResponse(CookieResponseC2SPacket packet, CallbackInfo ci) {
        var cookieHandled = ServerCookieEvents
                .getCOOKIE_RESPONSE()
                .invoker()
                .onCookieResponse(packet, ((ServerCommonNetworkHandler) (Object) this), this.transferred, this.server);
        if (cookieHandled) {
            ci.cancel();
        }
    }
}

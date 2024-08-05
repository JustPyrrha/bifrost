package gay.pyrrha.bifrost

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.network.packet.c2s.common.CookieResponseC2SPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerCommonNetworkHandler
import net.minecraft.text.Text

object ServerCookieEvents {
    @JvmStatic
    public val INVALID_PACKET_TEXT: Text = Text.translatable("multiplayer.disconnect.invalid_packet")

    @JvmStatic
    public val COOKIE_RESPONSE: Event<CookieResponse> =
        EventFactory.createArrayBacked(CookieResponse::class.java) { callbacks ->
            return@createArrayBacked CookieResponse { cookie, handler, transferred, server ->
                var handled = false
                callbacks.forEach {
                     handled = handled or it.onCookieResponse(
                        cookie,
                        handler,
                         transferred,
                        server
                    )
                }
                return@CookieResponse handled
            }
        }

    public fun interface CookieResponse {
        fun onCookieResponse(
            cookie: CookieResponseC2SPacket,
            handler: ServerCommonNetworkHandler,
            transferred: Boolean,
            server: MinecraftServer
        ): Boolean
    }
}
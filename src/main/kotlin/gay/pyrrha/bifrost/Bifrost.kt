package gay.pyrrha.bifrost

import io.github.nefilim.kjwt.JWSRSA512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.generateKeyPair
import io.github.nefilim.kjwt.sign
import io.github.nefilim.kjwt.verifySignature
import kotlinx.coroutines.runBlocking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.ClientConnection
import net.minecraft.network.DisconnectionInfo
import net.minecraft.network.listener.ClientQueryPacketListener
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket
import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket
import net.minecraft.network.packet.s2c.common.StoreCookieS2CPacket
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.util.profiler.MultiValueDebugSampleLogImpl
import java.net.InetSocketAddress
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import kotlin.jvm.optionals.getOrNull

public typealias RSAKeyPair = Pair<RSAPublicKey, RSAPrivateKey>

public object Bifrost : ModInitializer {
    private val jwtCookieId: Identifier = Identifier.of("bifrost", "jwt")
    private val jwtAlgorithm = JWSRSA512Algorithm
    public val serverKeyPair: RSAKeyPair = generateKeyPair(jwtAlgorithm)

    private val knownPublicKeys: HashMap<String, RSAPublicKey> = HashMap()

    override fun onInitialize() {
        ServerCookieEvents.COOKIE_RESPONSE.register { packet, handler, transferred, server ->
            println("cookie response with id ${packet.key}")
            if (packet.key != jwtCookieId) return@register false
            if (packet.payload == null) {
                val rawJwt = JWT.rs512 {
                    issuer(
                        "bifrost/${
                            FabricLoader.getInstance().getModContainer("bifrost").getOrNull()?.metadata?.version
                        }"
                    )
                    subject(handler.profile.id.toString())

                    claim("serverIp", server.serverIp)
                    claim("serverPort", server.serverPort)

                    issuedNow()
                }

                val (publicKey, privateKey) = serverKeyPair

                val signedJwt = runBlocking {
                    rawJwt.sign(privateKey).getOrNull()
                }

                if (signedJwt != null && verifySignature(
                        signedJwt.rendered,
                        publicKey,
                        jwtAlgorithm
                    ).isRight { it == signedJwt.jwt }
                ) {
                    handler.sendPacket(StoreCookieS2CPacket(jwtCookieId, signedJwt.rendered.toByteArray()))
                } else {
                    handler.disconnect(ServerCookieEvents.INVALID_PACKET_TEXT)
                }
            } else if (transferred && packet.payload != null) {
                // 1. check which server they came from
                // 2. request public key from original server
                // 3. verify jwt with original server's public key

                val payload = packet.payload.decodeToString()
                val jwt = JWT.decodeT(payload, jwtAlgorithm).getOrNull()
                if (jwt != null) {
                    val serverIp = jwt.claimValue("serverIp")
                    val serverPort = jwt.claimValueAsInt("serverPort")

                    if (serverIp.isNone() || serverPort.isNone()) {
                        return@register false
                    }

                    val connection = ClientConnection.connect(
                        InetSocketAddress.createUnresolved(
                            serverIp.getOrNull()!!,
                            serverPort.getOrNull()!!
                        ),
                        false,
                        (null as MultiValueDebugSampleLogImpl?)
                    )

                    val queryListener = object : ClientQueryPacketListener {
                        override fun onDisconnected(info: DisconnectionInfo) {
                            if (knownPublicKeys["${serverIp.getOrNull()!!}:${serverPort.getOrNull()!!}"] == null) {
                                // oh no
                                // todo: rerun request
                                println("oh god oh fuk")
                            }
                        }
                        override fun isConnectionOpen(): Boolean = connection.isOpen
                        var startPing = -0L
                        var sentQuery = false
                        override fun onPingResult(packet: PingResultS2CPacket) {
                            val time = Util.getMeasuringTimeMs()
                            val ping = time - startPing
                            println("ping: $ping")

                            if ((packet as PingResultS2CPacketInject).`bifrost$getEncodedPublicKey`() != null) {
                                try {
                                    knownPublicKeys["${serverIp.getOrNull()!!}:${serverPort.getOrNull()!!}"] =
                                        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec((packet as PingResultS2CPacketInject).`bifrost$getEncodedPublicKey`())) as RSAPublicKey
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                println("ping packet wasn't modified")
                            }
                        }

                        override fun onResponse(packet: QueryResponseS2CPacket) {
                            startPing = Util.getMeasuringTimeMs()
                            connection.send(QueryPingC2SPacket(startPing))
                            sentQuery = true
                        }
                    }

                    if (knownPublicKeys["${serverIp.getOrNull()!!}:${serverPort.getOrNull()!!}"] == null) {
                        try {
                            println("connecting")
                            connection.connect(serverIp.getOrNull()!!, serverPort.getOrNull()!!, queryListener)
                            println("connected")
                            connection.send(QueryRequestC2SPacket.INSTANCE)
                            println("sending request")
                        } catch (e: Exception) {
                            println(e.message)
                        }

                        while (knownPublicKeys["${serverIp.getOrNull()!!}:${serverPort.getOrNull()!!}"] == null) {
                            // wait for public key
                        }
                    } else {
                        println("what")
                    }

                    val publicKey = knownPublicKeys["${serverIp.getOrNull()!!}:${serverPort.getOrNull()!!}"]!!

                    val jwtValidated = verifySignature(jwt, publicKey).isRight()
                    println("validated: $jwtValidated")
                    return@register jwtValidated
                }

            } else {
                println("invalid state lol")
                return@register false // disconnect, invalid state
            }

            return@register true
        }

        ServerConfigurationConnectionEvents.CONFIGURE.register { handler, _ ->
            handler.sendPacket(CookieRequestS2CPacket(jwtCookieId))
        }
    }
}

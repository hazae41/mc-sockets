package hazae41.minecraft.sockets.bukkit

import hazae41.minecraft.kotlin.bukkit.*
import hazae41.minecraft.kotlin.textOf
import hazae41.minecraft.sockets.Sockets
import hazae41.minecraft.sockets.Sockets.onSocketEnable
import hazae41.sockets.*
import io.ktor.http.cio.websocket.send
import net.md_5.bungee.api.chat.ClickEvent
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

class Plugin : BukkitPlugin() {

    override fun onEnable() {
        update(15938)

        init(Config)

        Config.sockets.forEach { start(it) }

        command("sockets", permission = "sockets.list"){ args ->
            msg("Available sockets:")
            msg(Sockets.sockets.keys.joinToString(", "))
        }

        command("socket", permission = "sockets.info"){ args ->

            val name = args.getOrNull(0)
            ?: return@command msg("/socket <name> | /socket <name> key")

            val socket = Sockets.sockets[name]
            ?: return@command msg("Unknown socket")

            if(args.getOrNull(1) == "key"){
                val key = socket.key
                ?: return@command msg("This socket has no key")

                val keyStr = AES.toString(key)
                textOf("Click here to copy: $keyStr"){
                    clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, keyStr)
                    msg(this)
                }
            }

            else {
                msg("Available connections for $name:")
                msg(socket.connections.keys.joinToString(", "))
            }
        }

        if(Config.test){

            onSocketEnable {
                onConversation("/test"){
                    val (encrypt) = aes()
                    send("it works!".encrypt())
                }

                onConversation("/test/hello"){
                    println(readMessage())
                    send("hello back from $name")
                }
            }
        }
    }
}

object Config: ConfigFile("config"){
    val test by boolean("test")
    val Sockets = ConfigSection(this, "sockets")
    val sockets get() = Sockets.config.keys.map {
            name -> Socket(Sockets, name)
    }

    class Socket(config: ConfigSection, name: String): ConfigSection(config, name){
        val port by int("port")
        var key by string("key")

        val ConnectionsConfig = ConfigSection(this, "connections")
        val connections get() = ConnectionsConfig.config.keys.map {
                name -> Connection(ConnectionsConfig, name)
        }

        inner class Connection(config: ConfigSection, name: String): ConfigSection(config, name){
            val host by string("host")
            val port by int("port")
        }
    }
}

fun String.aes(): SecretKey {
    if(isBlank()) return AES.generate()
    return AES.toKey(this)
}

fun Plugin.start(config: Config.Socket) {
    val key = config.key.aes()
    if(config.key.isBlank()) config.key = AES.toString(key)

    val socket = Socket(config.port, key)
    Sockets.sockets[config.path] = socket

    config.connections.forEach {
        config ->socket.connectTo(config.path, config.host, config.port)
    }

    schedule(delay = 0, unit = TimeUnit.SECONDS) {
        Sockets.socketsNotifiers.forEach { it(socket, config.path) }
        socket.start()
        info("Started ${config.path}")
    }
}

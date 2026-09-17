package net.trilleo.mc.plugins.tritown

import net.trilleo.mc.plugins.tritown.config.PluginConfig
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import net.trilleo.mc.plugins.tritown.data.ServerDataManager
import net.trilleo.mc.plugins.tritown.registration.*
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.MessageUtil
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    lateinit var pluginConfig: PluginConfig
        private set

    override fun onEnable() {
        instance = this
        pluginConfig = PluginConfig(this)
        MessageUtil.init(pluginConfig.messagePrefix)

        ServerDataManager.init(this)
        PlayerDataManager.init(this)

        ItemRegistrar.registerAll(this)
        RecipeRegistrar.registerAll(this)

        CommandRegistrar.registerAll(this)
        PermissionRegistrar.registerAll(this)
        ListenerRegistrar.registerAll(this)
        GUIManager.registerAll(this)
        TaskRegistrar.registerAll(this)

        // Economy plugins may enable after TriTown, so the provider is only checked once every plugin has loaded.
        server.scheduler.runTask(this, Runnable {
            if (!EconomyUtil.isAvailable) {
                logger.severe("No Vault economy provider found. Install an economy plugin such as EssentialsX. Disabling TriTown.")
                server.pluginManager.disablePlugin(this)
            }
        })
    }

    /** Re-reads `config.yml` and applies the message prefix. */
    fun reload() {
        pluginConfig.reload()
        MessageUtil.init(pluginConfig.messagePrefix)
    }

    override fun onDisable() {
        TaskRegistrar.unregisterAll()
        RecipeRegistrar.unregisterAll()

        PlayerDataManager.saveAll()
        ServerDataManager.save()

        EconomyUtil.reset()
    }

    companion object {
        lateinit var instance: Main
            private set
    }
}

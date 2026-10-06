package net.trilleo.mc.plugins.tritown.commands.gathering

import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.guis.gathering.GatherListGUI
import net.trilleo.mc.plugins.tritown.guis.gathering.GatherRegionGUI
import net.trilleo.mc.plugins.tritown.guis.gathering.GatherRender
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Draws resource regions and opens their editor.
 *
 * What a region grows and spawns is set up in the menus; this covers what a
 * menu cannot — the wand, creating and deleting a region, and building inside
 * one.
 */
class GatherCommand : PluginCommand(
    name = "gather",
    description = "Set up resource regions",
    usage = "/tritown gather <list|wand|create|edit|delete|build|show|regrow>",
    permission = GatherEditors.ADMIN_PERMISSION,
) {

    override val extraPermissions =
        ACTIONS.map { permissionFor(it) } + GatherRender.EDIT_PERMISSION + GatherAccess.BYPASS_PERMISSION

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!GatherManager.isReady) {
            sender.sendPrefixed(sender.tr("command.gather.unavailable"))
            return true
        }

        val action = args.firstOrNull()?.lowercase()
        if (action == null || action !in ACTIONS) {
            sendUsage(sender)
            return true
        }

        if (!sender.hasPermission(permissionFor(action))) {
            sender.sendPrefixed(sender.tr("command.gather.no-permission-action", "action" to action))
            return true
        }

        val player = sender as? Player ?: run {
            if (action == "delete" || action == "regrow") {
                run(sender, action, args)
            } else {
                sender.sendPrefixed(sender.tr("command.gather.players-only"))
            }
            return true
        }

        run(player, action, args)
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = when (args.size) {
        1 -> ACTIONS.filter { sender.hasPermission(permissionFor(it)) && it.startsWith(args[0], ignoreCase = true) }
        2 -> when (args[0].lowercase()) {
            "edit", "delete", "show", "regrow" -> GatherManager.ids().filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }

        3 -> if (args[0].equals("delete", ignoreCase = true)) {
            listOf(CONFIRM).filter { it.startsWith(args[2], ignoreCase = true) }
        } else emptyList()

        else -> emptyList()
    }

    private fun run(sender: CommandSender, action: String, args: Array<out String>) {
        when (action) {
            "list" -> GatherListGUI.show(sender as Player)
            "wand" -> wand(sender as Player)
            "create" -> create(sender as Player, args)
            "edit" -> edit(sender as Player, args)
            "delete" -> delete(sender, args)
            "build" -> build(sender as Player)
            "show" -> show(sender as Player, args)
            "regrow" -> regrow(sender, args)
        }
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private fun wand(player: Player) {
        InventoryUtil.give(player, listOf(GatherEditors.wand()))
        player.sendPrefixed(player.tr("command.gather.wand"))
    }

    private fun create(player: Player, args: Array<out String>) {
        val id = args.getOrNull(1)?.lowercase()
        if (id == null) {
            player.sendPrefixed(player.tr("command.gather.usage.create"))
            return
        }
        if (!ResourceRegion.isValidId(id) || GatherManager.get(id) != null) {
            player.sendPrefixed(player.tr("command.gather.create-failed", "id" to id))
            return
        }

        val area = GatherEditors.selection(player).area()
        val result = AreaCheck.check(area)
        if (result !is AreaCheck.Result.Ok) {
            player.sendPrefixed(AreaCheck.message(player, result))
            return
        }

        val name = args.drop(2).joinToString(" ").ifBlank { id }
        val region = GatherManager.create(id, name, area!!, result.townId) ?: run {
            player.sendPrefixed(player.tr("command.gather.create-failed", "id" to id))
            return
        }

        player.sendPrefixed(
            player.tr("command.gather.created", "id" to region.id, "town" to GatherAccess.townName(player, region))
        )
        GatherRegionGUI.show(player, region)
    }

    /** Opens the region named, or the one the player is standing in. */
    private fun edit(player: Player, args: Array<out String>) {
        val region = args.getOrNull(1)?.let { resolve(player, it) ?: return } ?: GatherManager.at(player.location)
        if (region == null) {
            player.sendPrefixed(player.tr("command.gather.not-in-region"))
            return
        }
        GatherRegionGUI.show(player, region)
    }

    /**
     * Deletes a region once the word is typed out. Everything it harvested is
     * grown back first, and its spawners' mobs taken away, so the ground is
     * left as it was built.
     */
    private fun delete(sender: CommandSender, args: Array<out String>) {
        val region = resolve(sender, args.getOrNull(1) ?: return sendUsage(sender)) ?: return

        if (!args.getOrNull(2).equals(CONFIRM, ignoreCase = true)) {
            sender.sendPrefixed(sender.tr("command.gather.delete-confirm", "id" to region.id))
            return
        }

        Spawners.clear(region)
        GatherManager.delete(region.id)
        sender.sendPrefixed(sender.tr("command.gather.deleted", "id" to region.id))
    }

    private fun build(player: Player) {
        val building = GatherEditors.toggleBuilding(player)
        player.sendPrefixed(player.tr(if (building) "command.gather.build-on" else "command.gather.build-off"))
    }

    private fun show(player: Player, args: Array<out String>) {
        val region = args.getOrNull(1)?.let { resolve(player, it) ?: return } ?: GatherManager.at(player.location)
        val area = region?.area ?: GatherEditors.selection(player).area()
        if (area == null) {
            player.sendPrefixed(player.tr("command.gather.not-in-region"))
            return
        }
        GatherEditors.outline(player, area, OUTLINE_SECONDS)
        player.sendPrefixed(player.tr("gathering.editor.outlined", "seconds" to OUTLINE_SECONDS))
    }

    private fun regrow(sender: CommandSender, args: Array<out String>) {
        val region = resolve(sender, args.getOrNull(1) ?: return sendUsage(sender)) ?: return
        val count = Regrowth.regrowAll(region)
        sender.sendPrefixed(sender.tr("gathering.editor.regrown", "amount" to count))
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun resolve(sender: CommandSender, id: String): ResourceRegion? {
        val region = GatherManager.get(id)
        if (region == null) sender.sendPrefixed(sender.tr("command.gather.unknown", "id" to id))
        return region
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendPrefixed(sender.tr("command.gather.usage.header"))
        for (action in ACTIONS) {
            if (sender.hasPermission(permissionFor(action))) sender.sendPrefixed(usageFor(sender, action))
        }
    }

    /** Spelled out rather than built from the action, so every line is a key the translation test can see. */
    private fun usageFor(sender: CommandSender, action: String): String = when (action) {
        "list" -> sender.tr("command.gather.usage.list")
        "wand" -> sender.tr("command.gather.usage.wand")
        "create" -> sender.tr("command.gather.usage.create")
        "edit" -> sender.tr("command.gather.usage.edit")
        "delete" -> sender.tr("command.gather.usage.delete")
        "build" -> sender.tr("command.gather.usage.build")
        "show" -> sender.tr("command.gather.usage.show")
        else -> sender.tr("command.gather.usage.regrow")
    }

    private companion object {
        const val CONFIRM = "confirm"
        const val OUTLINE_SECONDS = 15

        val ACTIONS = listOf("list", "wand", "create", "edit", "delete", "build", "show", "regrow")

        fun permissionFor(action: String): String = "${GatherEditors.ADMIN_PERMISSION}.$action"
    }
}

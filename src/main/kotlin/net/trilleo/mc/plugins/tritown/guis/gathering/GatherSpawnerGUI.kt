package net.trilleo.mc.plugins.tritown.guis.gathering

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import kotlin.math.floor

/** One spawner: how many mobs it keeps, how quickly it replaces them, how far they roam, and how strong they are. */
class GatherSpawnerGUI : PluginGUI(
    id = ID,
    titleKey = "gui.gather-spawner.title",
    rows = 6,
) {

    private enum class Button { MAX, RESPAWN, RADIUS, LEVEL, CATEGORY, MOVE, DELETE, BACK }

    /** A number an administrator types in chat, with its question and its limits. */
    private enum class Field(val min: Int, val max: Int) {
        MAX(1, 32), RESPAWN(1, 86_400), RADIUS(1, 32), LEVEL(1, 200)
    }

    private val layout: Map<Int, Button> = buildMap {
        listOf(Button.MAX, Button.RESPAWN, Button.RADIUS, Button.LEVEL)
            .zip(GUIFrame.spacedColumns(4)).forEach { (button, column) -> put(3 * ROW_SIZE + column, button) }
        listOf(Button.CATEGORY, Button.MOVE, Button.DELETE)
            .zip(GUIFrame.spacedColumns(3)).forEach { (button, column) -> put(4 * ROW_SIZE + column, button) }
        put(BACK_SLOT, Button.BACK)
    }

    override fun title(player: Player): Component {
        val mob = GatherRender.spawner(player)?.type?.let(GatherRender::entityName) ?: ""
        return ComponentUtil.parse(player.tr("gui.gather-spawner.title", "mob" to mob))
    }

    override fun setup(player: Player, inventory: Inventory) {
        val region = GatherRender.region(player) ?: return
        val spawner = GatherRender.spawner(player) ?: return
        GUIFrame.draw(inventory, layout.keys + INFO_SLOT)
        inventory.setItem(
            INFO_SLOT,
            GatherRender.card(
                GatherRender.eggOf(spawner.type),
                player.tr("gui.gather-spawners.name", "mob" to GatherRender.entityName(spawner.type)),
                summary(player, spawner) + player.tr("gui.gather-spawner.alive", "amount" to Spawners.count(spawner)),
            ),
        )
        layout.forEach { (slot, button) -> inventory.setItem(slot, render(player, region, spawner, button)) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return
        val player = event.whoClicked as? Player ?: return
        if (!GatherRender.mayEdit(player)) return
        val region = GatherRender.region(player) ?: return
        val spawner = GatherRender.spawner(player) ?: return

        when (layout[event.rawSlot]) {
            Button.MAX -> prompt(player, region, spawner, Field.MAX)
            Button.RESPAWN -> prompt(player, region, spawner, Field.RESPAWN)
            Button.RADIUS -> prompt(player, region, spawner, Field.RADIUS)
            Button.LEVEL -> prompt(player, region, spawner, Field.LEVEL)
            Button.CATEGORY -> {
                spawner.category = spawner.category.next()
                GatherManager.save()
                GatherRender.click(player)
                GUIManager.refresh(player)
            }

            Button.MOVE -> {
                val at = player.location
                if (!region.area.contains(at)) {
                    player.sendPrefixed(player.tr("gathering.editor.stand-inside"))
                    return
                }
                Spawners.clear(spawner)
                spawner.x = at.blockX + 0.5
                spawner.y = at.blockY.toDouble()
                spawner.z = at.blockZ + 0.5
                GatherManager.save()
                player.sendPrefixed(player.tr("gathering.editor.spawner-moved"))
                GUIManager.refresh(player)
            }

            Button.DELETE -> if (event.click == ClickType.SHIFT_LEFT) {
                GatherSpawnerListGUI.remove(player, region, spawner)
                GatherRender.navigate { GatherSpawnerListGUI.show(player, region) }
            }

            Button.BACK -> {
                GatherRender.click(player)
                GatherRender.navigate { GatherSpawnerListGUI.show(player, region) }
            }

            null -> Unit
        }
    }

    private fun prompt(player: Player, region: ResourceRegion, spawner: ResourceSpawner, field: Field) {
        val question = when (field) {
            Field.MAX -> player.tr("gathering.editor.prompt-max", "max" to field.max)
            Field.RESPAWN -> player.tr("gathering.editor.prompt-respawn")
            Field.RADIUS -> player.tr("gathering.editor.prompt-radius", "max" to field.max)
            Field.LEVEL -> player.tr("gathering.editor.prompt-level", "max" to field.max)
        }

        GatherRender.navigate {
            player.closeInventory()
            ChatPrompt.ask(player, question) { input ->
                val value = if (field == Field.RESPAWN) GatherRender.parseSeconds(input) else input.trim().toIntOrNull()
                if (value == null || value !in field.min..field.max) {
                    player.sendPrefixed(player.tr("common.invalid-amount"))
                } else {
                    when (field) {
                        Field.MAX -> spawner.maxAlive = value
                        Field.RESPAWN -> spawner.respawnSeconds = value
                        Field.RADIUS -> spawner.radius = value
                        Field.LEVEL -> spawner.level = value
                    }
                    GatherManager.save()
                }
                show(player, region, spawner)
            }
        }
    }

    private fun render(player: Player, region: ResourceRegion, spawner: ResourceSpawner, button: Button): ItemStack =
        when (button) {
            Button.MAX -> GatherRender.button(
                player, Material.EGG, "gui.gather-spawner.max",
                listOf(
                    player.tr("gui.gather-node.current", "value" to spawner.maxAlive),
                    player.tr("gui.gather-spawner.max-lore"),
                ),
            )

            Button.RESPAWN -> GatherRender.button(
                player, Material.CLOCK, "gui.gather-spawner.respawn",
                listOf(
                    player.tr("gui.gather-node.current", "value" to GatherRender.duration(player, spawner.respawnSeconds)),
                    player.tr("gui.gather-spawner.respawn-lore"),
                ),
            )

            Button.RADIUS -> GatherRender.button(
                player, Material.LEAD, "gui.gather-spawner.radius",
                listOf(
                    player.tr("gui.gather-node.current", "value" to spawner.radius),
                    player.tr("gui.gather-spawner.radius-lore"),
                ),
            )

            Button.LEVEL -> GatherRender.button(
                player, Material.EXPERIENCE_BOTTLE, "gui.gather-spawner.level",
                listOf(
                    player.tr("gui.gather-node.current", "value" to spawner.level),
                    player.tr("gui.gather-spawner.level-lore"),
                ),
            )

            Button.CATEGORY -> GatherRender.button(
                player, spawner.category.icon, "gui.gather-node.category",
                listOf(
                    player.tr("gui.gather-node.current", "value" to player.tr(spawner.category.key)),
                    player.tr("gui.gather-node.category-lore"),
                ),
            )

            Button.MOVE -> GatherRender.button(
                player, Material.COMPASS, "gui.gather-spawner.move",
                listOf(player.tr("gui.gather-spawner.move-lore")),
            )

            Button.DELETE -> GatherRender.button(
                player, Material.LAVA_BUCKET, "gui.gather-spawner.delete",
                listOf(player.tr("gui.gather-spawner.delete-lore")),
            )

            Button.BACK -> GatherRender.button(
                player, Material.ARROW, "gui.gather-spawner.back",
                listOf(player.tr("gui.gather-spawner.back-lore", "id" to region.id)),
            )
        }

    companion object {
        const val ID = "gather-spawner"

        private const val ROW_SIZE = 9
        private const val INFO_SLOT = 13
        private const val BACK_SLOT = 49

        fun show(player: Player, region: ResourceRegion, spawner: ResourceSpawner): Boolean {
            GatherRender.setContext(player, GatherRender.Context(region.id, spawnerId = spawner.id))
            return GUIManager.open(player, ID)
        }

        fun summary(player: Player, spawner: ResourceSpawner): List<String> = listOf(
            player.tr("gui.gather-node.line-category", "value" to player.tr(spawner.category.key)),
            player.tr("gui.gather-spawner.line-max", "amount" to spawner.maxAlive),
            player.tr("gui.gather-spawner.line-respawn", "value" to GatherRender.duration(player, spawner.respawnSeconds)),
            player.tr("gui.gather-spawner.line-radius", "amount" to spawner.radius),
            player.tr("gui.gather-spawner.line-level", "amount" to spawner.level),
            player.tr(
                "gui.gather-spawner.line-at",
                "x" to floor(spawner.x).toInt(), "y" to floor(spawner.y).toInt(), "z" to floor(spawner.z).toInt(),
            ),
        )
    }
}

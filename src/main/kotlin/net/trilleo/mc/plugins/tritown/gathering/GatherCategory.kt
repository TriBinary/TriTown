package net.trilleo.mc.plugins.tritown.gathering

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Enemy
import org.bukkit.entity.EntityType

/**
 * What kind of gathering a resource is: what a region advertises and what a
 * player's tally is kept under.
 *
 * The first four are for blocks, the last two for what a spawner calls up.
 */
enum class GatherCategory(val key: String, val icon: Material, val forBlocks: Boolean) {
    MINING("gathering.category.mining", Material.IRON_PICKAXE, true),
    FORAGING("gathering.category.foraging", Material.IRON_AXE, true),
    FARMING("gathering.category.farming", Material.IRON_HOE, true),
    EXCAVATION("gathering.category.excavation", Material.IRON_SHOVEL, true),
    HUSBANDRY("gathering.category.husbandry", Material.WHEAT, false),
    COMBAT("gathering.category.combat", Material.IRON_SWORD, false);

    /** The next category of the same family, for a button that cycles through them. */
    fun next(): GatherCategory {
        val family = entries.filter { it.forBlocks == forBlocks }
        return family[(family.indexOf(this) + 1) % family.size]
    }

    companion object {

        /** A first guess at a block's category, which the administrator can change. */
        fun of(material: Material): GatherCategory = when {
            Tag.LOGS.isTagged(material) || Tag.LEAVES.isTagged(material) || material == Material.BAMBOO -> FORAGING
            material.isBlock && material.createBlockData() is Ageable -> FARMING
            material in FARMED -> FARMING
            Tag.MINEABLE_SHOVEL.isTagged(material) -> EXCAVATION
            Tag.MINEABLE_AXE.isTagged(material) -> FORAGING
            else -> MINING
        }

        /** A hostile mob is fought for; anything else is kept. */
        fun of(type: EntityType): GatherCategory =
            if (type.entityClass?.let { Enemy::class.java.isAssignableFrom(it) } == true) COMBAT else HUSBANDRY

        private val FARMED = setOf(Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS)
    }
}

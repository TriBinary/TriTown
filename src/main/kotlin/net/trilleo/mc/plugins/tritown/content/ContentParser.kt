package net.trilleo.mc.plugins.tritown.content

/**
 * Reads `items.yml` and `mobs.yml` into their models.
 *
 * Plain Kotlin over what SnakeYAML parses, like [BalanceParser], so the bundled
 * files are checked in tests. An entry that cannot be used is left out and
 * named in the result's warnings, rather than taking the rest of the file down
 * with it.
 */
object ContentParser {

    class Result<T>(val value: T, val warnings: List<String>)

    fun items(root: Map<*, *>): Result<Map<String, ContentItemDef>> {
        val reader = YamlReader(root)
        val items = linkedMapOf<String, ContentItemDef>()

        for (id in reader.keys(listOf("items"))) {
            val path = listOf("items", id)
            if (!ContentItemDef.ID.matches(id)) {
                reader.warn(path, "an id may only use lower-case letters, digits and dashes; left out")
                continue
            }
            val model = reader.text(path + "model") ?: run {
                reader.warn(path, "has no model; left out")
                continue
            }
            val rarityName = reader.text(path + "rarity") ?: Rarity.COMMON.name
            val rarity = Rarity.of(rarityName) ?: run {
                reader.warn(path + "rarity", "no rarity is called '$rarityName'; using common")
                Rarity.COMMON
            }
            items[id] = ContentItemDef(id, model.lowercase(), rarity, reader.boolean(path + "glint", false))
        }
        return Result(items, reader.warnings)
    }

    /** `mobs.yml`, checked against the ids of the [items] it may name. */
    fun loot(root: Map<*, *>, items: Set<String>): Result<LootTable> {
        val reader = YamlReader(root)
        val default = LootTable.EMPTY

        val families = linkedMapOf<String, LootTable.Family>()
        for (id in reader.keys(listOf("families"))) {
            val path = listOf("families", id)
            val material = reader.text(path + "material")
            if (material == null || material !in items) {
                reader.warn(path + "material", "'$material' is not an item in items.yml; left out")
                continue
            }
            for (mob in reader.texts(path + "mobs").map { it.uppercase() }) {
                val taken = families[mob]
                if (taken != null) {
                    reader.warn(path + "mobs", "$mob already drops ${taken.material} for ${taken.id}; left there")
                    continue
                }
                families[mob] = LootTable.Family(id, material)
            }
        }

        val essence = reader.sections(listOf("essence")).mapIndexedNotNull { index, section ->
            val grade = YamlReader(section)
            val item = grade.text(listOf("item"))
            if (item == null || item !in items) {
                reader.warn(listOf("essence", "$index"), "'$item' is not an item in items.yml; left out")
                return@mapIndexedNotNull null
            }
            LootTable.EssenceGrade(item, grade.integer(listOf("max-level"), Int.MAX_VALUE, min = 1))
        }.sortedBy { it.maxLevel }

        val table = LootTable(
            families = families,
            rules = LootTable.Rules(
                minLevel = reader.integer(listOf("loot", "min-level"), default.rules.minLevel, min = 1),
                playerShare = reader.number(listOf("loot", "player-share"), default.rules.playerShare, min = 0.0),
                magicFindCap = reader.number(listOf("loot", "magic-find-cap"), default.rules.magicFindCap, min = 0.0),
            ),
            normal = drop(reader, "normal"),
            elite = drop(reader, "elite"),
            champion = drop(reader, "champion"),
            essence = essence,
        )
        return Result(table, reader.warnings)
    }

    private fun drop(reader: YamlReader, rank: String): LootTable.Drop {
        val path = listOf("loot", rank)
        val amount = reader.range(path + "amount", 1..1, min = 0)
        val essence = reader.range(path + "essence", 0..0, min = 0)
        return LootTable.Drop(
            chance = reader.number(path + "chance", 0.0, min = 0.0),
            minAmount = amount.first,
            maxAmount = amount.last,
            minEssence = essence.first,
            maxEssence = essence.last,
        )
    }
}

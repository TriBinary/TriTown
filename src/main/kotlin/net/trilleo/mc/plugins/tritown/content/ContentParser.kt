package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.combat.Stat

/**
 * Reads `items.yml`, `mobs.yml` and `gear.yml` into their models.
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

    /** `gear.yml`, checked against the ids of the [items] a recipe may name. */
    fun gear(root: Map<*, *>, items: Set<String>): Result<GearCatalog> {
        val reader = YamlReader(root)

        val reforges = linkedMapOf<String, ReforgeDef>()
        for (id in reader.keys(listOf("reforges"))) {
            val path = listOf("reforges", id)
            if (!ContentItemDef.ID.matches(id)) {
                reader.warn(path, "an id may only use lower-case letters, digits and dashes; left out")
                continue
            }
            val slots = reader.texts(path + "slots").mapNotNull { name ->
                GearSlot.of(name) ?: run {
                    reader.warn(path + "slots", "no slot is called '$name'; left out")
                    null
                }
            }.toSet()
            val weights = weights(reader, path + "weights") ?: continue
            if (slots.isEmpty()) {
                reader.warn(path, "fits no slot; left out")
                continue
            }
            reforges[id] = ReforgeDef(id, slots, weights)
        }

        val gear = linkedMapOf<String, GearDef>()
        for (id in reader.keys(listOf("gear"))) {
            val path = listOf("gear", id)
            if (!ContentItemDef.ID.matches(id)) {
                reader.warn(path, "an id may only use lower-case letters, digits and dashes; left out")
                continue
            }
            val slotName = reader.text(path + "slot")
            val slot = slotName?.let(GearSlot::of) ?: run {
                reader.warn(path + "slot", "no slot is called '$slotName'; left out")
                continue
            }
            val base = reader.text(path + "base") ?: run {
                reader.warn(path, "has no base item; left out")
                continue
            }
            val weights = weights(reader, path + "weights") ?: continue

            gear[id] = GearDef(
                id = id,
                slot = slot,
                tier = reader.integer(path + "tier", 1, min = 1),
                base = base.uppercase(),
                model = reader.text(path + "model")?.lowercase(),
                trim = trim(reader, path + "trim"),
                dye = reader.text(path + "dye")?.let { dye(reader, path + "dye", it) },
                weights = weights,
                recipe = recipe(reader, path + "recipe", items),
            )
        }
        return Result(GearCatalog(gear, reforges), reader.warnings)
    }

    /** Stat weights, scaled to sum to 1, or `null` (and a warning) when there are none to use. */
    private fun weights(reader: YamlReader, path: List<String>): Map<Stat, Double>? {
        val raw = reader.keys(path).mapNotNull { name ->
            val stat = Stat.entries.firstOrNull { BalanceParser.name(it) == name } ?: run {
                reader.warn(path + name, "no stat is called '$name'; left out")
                return@mapNotNull null
            }
            stat to reader.number(path + name, 0.0, min = 0.0)
        }.filter { it.second > 0.0 }
        val total = raw.sumOf { it.second }
        if (total <= 0.0) {
            reader.warn(path, "gives no stat any weight; left out")
            return null
        }
        return raw.associate { (stat, weight) -> stat to weight / total }
    }

    private fun trim(reader: YamlReader, path: List<String>): GearDef.Trim? {
        val pattern = reader.text(path + "pattern") ?: return null
        val material = reader.text(path + "material") ?: run {
            reader.warn(path, "a trim needs both a pattern and a material; left off")
            return null
        }
        return GearDef.Trim(pattern.lowercase(), material.lowercase())
    }

    private fun dye(reader: YamlReader, path: List<String>, text: String): Int? {
        val rgb = text.removePrefix("#").toIntOrNull(16)?.takeIf { text.removePrefix("#").length == 6 }
        if (rgb == null) reader.warn(path, "'$text' is not a colour such as '#3a5f8c'; left undyed")
        return rgb
    }

    private fun recipe(reader: YamlReader, path: List<String>, items: Set<String>): GearDef.Recipe? {
        if (reader.keys(path).isEmpty()) return null
        val needs = reader.keys(path + "items").mapNotNull { id ->
            if (id !in items) {
                reader.warn(path + "items" + id, "'$id' is not an item in items.yml; the recipe is left out")
                return null
            }
            id to reader.integer(path + "items" + id, 1, min = 1)
        }.toMap()
        return GearDef.Recipe(needs, reader.number(path + "money", 0.0, min = 0.0))
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
            gearChance = reader.number(path + "gear-chance", 0.0, min = 0.0),
        )
    }
}

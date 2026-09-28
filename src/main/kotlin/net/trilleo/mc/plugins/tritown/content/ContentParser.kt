package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.mobs.Ability
import net.trilleo.mc.plugins.tritown.mobs.Affix

/**
 * Reads `items.yml`, `mobs.yml`, `gear.yml` and `bestiary.yml` into their models.
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

    /**
     * `bestiary.yml`, checked against the ids of the [items] and [gear] its
     * costumes and loot may name. What only a server can check — that a base is
     * a kind of mob, a biome exists, a costume's item is real — is left to
     * `ContentRegistry`.
     */
    fun bestiary(root: Map<*, *>, items: Set<String>, gear: Set<String>): Result<BestiaryCatalog> {
        val reader = YamlReader(root)
        val kinds = linkedMapOf<String, MobKindDef>()

        for (id in reader.keys(listOf("variants"))) {
            val path = listOf("variants", id)
            if (!ContentItemDef.ID.matches(id)) {
                reader.warn(path, "an id may only use lower-case letters, digits and dashes; left out")
                continue
            }
            val base = reader.text(path + "base") ?: run {
                reader.warn(path, "has no base kind of mob; left out")
                continue
            }
            if (reader.keys(path + "spawn").isEmpty()) {
                reader.warn(path, "has no spawn section, so it would never appear; left out")
                continue
            }
            kinds[id] = kind(reader, path, id, base, items, gear).copy(
                spawn = spawnRule(reader, path + "spawn"),
                rankable = reader.boolean(path + "rankable", true),
            )
        }

        for (id in reader.keys(listOf("bosses"))) {
            val path = listOf("bosses", id)
            if (!ContentItemDef.ID.matches(id) || id in kinds) {
                reader.warn(path, "an id must be new and use only lower-case letters, digits and dashes; left out")
                continue
            }
            val base = reader.text(path + "base") ?: run {
                reader.warn(path, "has no base kind of mob; left out")
                continue
            }
            val sigil = reader.text(path + "sigil")
            if (sigil == null || sigil !in items) {
                reader.warn(
                    path + "sigil",
                    "'$sigil' is not an item in items.yml, so nothing could summon it; left out"
                )
                continue
            }
            if (kinds.values.any { it.boss?.sigil == sigil }) {
                reader.warn(path + "sigil", "'$sigil' already summons another boss; left out")
                continue
            }
            kinds[id] = kind(reader, path, id, base, items, gear).copy(boss = boss(reader, path, sigil))
        }

        val named = kinds.mapValues { (id, def) ->
            if (def.minions == null || def.minions in kinds) return@mapValues def
            val section = if (def.boss != null) "bosses" else "variants"
            reader.warn(
                listOf(section, id, "minions"),
                "'${def.minions}' is not a custom mob here; it calls its own kind"
            )
            def.copy(minions = null)
        }
        return Result(BestiaryCatalog(named, bossRules(reader)), reader.warnings)
    }

    /** What every custom mob has, variant or boss, as neither: the caller says which it is. */
    private fun kind(
        reader: YamlReader,
        path: List<String>,
        id: String,
        base: String,
        items: Set<String>,
        gear: Set<String>,
    ) = MobKindDef(
        id = id,
        base = base.uppercase(),
        spawn = null,
        boss = null,
        rankable = false,
        health = reader.number(path + "health", 1.0, min = 0.1),
        damage = reader.number(path + "damage", 1.0, min = 0.1),
        defense = reader.number(path + "defense", 0.0, min = 0.0),
        speed = reader.number(path + "speed", 0.0, min = -90.0),
        scale = reader.number(path + "scale", 1.0, min = 0.1),
        knockback = reader.number(path + "knockback", 0.0, min = 0.0).coerceAtMost(100.0),
        affixes = affixes(reader, path + "affixes"),
        abilities = abilities(reader, path + "abilities"),
        minions = reader.text(path + "minions")?.lowercase(),
        equipment = equipment(reader, path + "equipment", gear),
        loot = kindLoot(reader, path + "loot", items, gear),
    )

    private fun boss(reader: YamlReader, path: List<String>, sigil: String): BossDef {
        val barName = reader.text(path + "bar")?.lowercase()
        val bar = barName?.takeIf { it in BossDef.BAR_COLORS } ?: run {
            if (barName != null) reader.warn(path + "bar", "no boss bar colour is called '$barName'; using red")
            "red"
        }
        val phases = reader.sections(path + "phases").mapIndexed { index, section ->
            val phase = YamlReader(section)
            val at = path + "phases" + "$index"
            BossPhase(
                below = phase.number(listOf("below"), 0.0, min = 0.0).coerceAtMost(100.0),
                affixes = affixes(phase, listOf("affixes")),
                abilities = abilities(phase, listOf("abilities")),
                summon = phase.integer(listOf("summon"), 0, min = 0),
            ).also { phase.warnings.forEach { reader.warn(at, it) } }
        }
        val place = path + "place"
        return BossDef(
            level = reader.integer(path + "level", 1, min = 1),
            sigil = sigil,
            place = SummonPlace(
                worlds = reader.texts(place + "worlds").map { it.lowercase() }.toSet(),
                water = reader.boolean(place + "water", false),
                maxY = height(reader, place + "max-y"),
            ),
            arena = reader.number(path + "arena", 24.0, min = 4.0),
            bar = bar,
            phases = phases.sortedByDescending { it.below },
        )
    }

    private fun bossRules(reader: YamlReader): BossRules {
        val path = listOf("rules")
        val default = BossRules.DEFAULT
        val odds = reader.keys(path + "gear-odds").mapNotNull { name ->
            val rarity = Rarity.of(name) ?: run {
                reader.warn(path + "gear-odds" + name, "no rarity is called '$name'; left out")
                return@mapNotNull null
            }
            rarity to reader.number(path + "gear-odds" + name, 0.0, min = 0.0)
        }.toMap()
        return BossRules(
            contributorShare = reader.number(path + "contributor-share", default.contributorShare, min = 0.0),
            lootRange = reader.number(path + "loot-range", default.lootRange, min = 0.0),
            idleSeconds = reader.integer(path + "idle-seconds", default.idleSeconds, min = 1),
            ritualSeconds = reader.integer(path + "ritual-seconds", default.ritualSeconds, min = 0),
            arenaMargin = reader.number(path + "arena-margin", default.arenaMargin, min = 0.0),
            gearChance = reader.number(path + "gear-chance", default.gearChance, min = 0.0),
            gearOdds = odds.ifEmpty { default.gearOdds },
        )
    }

    private fun spawnRule(reader: YamlReader, path: List<String>): SpawnRule {
        val levels = reader.range(path + "levels", 1..Int.MAX_VALUE, min = 1)
        val timeName = reader.text(path + "time")
        val time = timeName?.let { name ->
            SpawnTime.of(name) ?: run {
                reader.warn(path + "time", "must be any, day or night, not '$name'; using any")
                null
            }
        } ?: SpawnTime.ANY
        return SpawnRule(
            worlds = reader.texts(path + "worlds").map { it.lowercase() }.toSet(),
            biomes = reader.texts(path + "biomes").map { biome ->
                biome.lowercase().let { if (':' in it) it else "minecraft:$it" }
            }.toSet(),
            minLevel = levels.first,
            maxLevel = levels.last,
            chance = reader.number(path + "chance", 0.0, min = 0.0),
            time = time,
            minY = height(reader, path + "min-y"),
            maxY = height(reader, path + "max-y"),
        )
    }

    private fun height(reader: YamlReader, path: List<String>): Int? =
        if (reader.text(path) == null) null else reader.integer(path, 0, min = Int.MIN_VALUE)

    private fun affixes(reader: YamlReader, path: List<String>): Set<Affix> =
        reader.texts(path).mapNotNull { name ->
            Affix.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: run {
                reader.warn(path, "no affix is called '$name'; left out")
                null
            }
        }.toSet()

    private fun abilities(reader: YamlReader, path: List<String>): List<Ability> =
        reader.texts(path).mapNotNull { name ->
            Ability.of(name) ?: run {
                reader.warn(path, "no ability is called '$name'; left out")
                null
            }
        }.distinct()

    /** `gear:<id>` for a piece of gear's look, or a vanilla item's name. */
    private fun equipment(reader: YamlReader, path: List<String>, gear: Set<String>): Map<CostumeSlot, Costume> =
        reader.keys(path).mapNotNull { name ->
            val slot = CostumeSlot.of(name) ?: run {
                reader.warn(path + name, "no slot is called '$name'; left out")
                return@mapNotNull null
            }
            val text = reader.text(path + name) ?: return@mapNotNull null
            val costume = if (text.startsWith(GEAR_PREFIX)) {
                val id = text.removePrefix(GEAR_PREFIX)
                if (id !in gear) {
                    reader.warn(path + name, "'$id' is not gear in gear.yml; left bare")
                    return@mapNotNull null
                }
                Costume.Gear(id)
            } else Costume.Vanilla(text.uppercase())
            slot to costume
        }.toMap()

    private fun kindLoot(
        reader: YamlReader,
        path: List<String>,
        items: Set<String>,
        gear: Set<String>
    ): List<LootEntry> =
        reader.sections(path).mapIndexedNotNull { index, section ->
            val entry = YamlReader(section)
            val at = path + "$index"
            val chance = entry.number(listOf("chance"), 0.0, min = 0.0)
            val item = entry.text(listOf("item"))
            val piece = entry.text(listOf("gear"))
            val result = when {
                item != null && item in items -> {
                    val amount = entry.range(listOf("amount"), 1..1, min = 1)
                    LootEntry.Item(item, chance, amount.first, amount.last)
                }

                piece != null && piece in gear -> LootEntry.Gear(piece, chance)
                else -> {
                    reader.warn(at, "names neither an item in items.yml nor gear in gear.yml; left out")
                    null
                }
            }
            entry.warnings.forEach { reader.warn(at, it) }
            result
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

    private const val GEAR_PREFIX = "gear:"
}

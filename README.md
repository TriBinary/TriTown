<h1 align="center">
  TriTown
</h1>

<p align="center">
  A <a href="https://github.com/TownyAdvanced/Towny">Towny</a> addon with custom town features for the Trilleo server.
</p>

---

## Features

TriTown is in early development; see the [change log](CHANGELOG.md) for what has shipped.

**A menu in every hotbar.** Every player carries a glowing item in the last slot of their hotbar; right-click it, or
run `/tt menu`, for everything TriTown offers in one place. Your profile sits at the top — balance, founding credit,
leaderboard rank, town and nation — beside it your town's resource regions, when it has any, and below it are your town at a glance with a shortcut into
[TownyMenu](https://github.com/Trilleo/TownyMenu), your storage, the global shop, a list of the players near enough to trade with
(anyone waiting for your answer first), a list of players to pay, the richest players as heads, the server's vital signs,
the Forge, the server news, your bestiary, a sidebar switch, and the admin panel for those allowed it. Anything switched off on the server, or that you may not
use, is simply left out, and what remains is centred. The item cannot be moved, dropped, stored, crafted with or handed
to anything, a copy made any other way is deleted within a second, and it is taken off you when you log out, so there
is nothing to duplicate and nothing left behind if TriTown is ever removed.

**A built-in economy.** TriTown supplies the server's Vault economy itself, so Towny gets working player wallets and
town and nation banks without a separate economy plugin such as EssentialsX. Balances are stored as whole units of the
smallest denomination, so they never drift, and they are written to disk atomically with a backup copy. The currency,
starting balance, balance cap and formatting are all configurable. If you would rather keep another economy plugin,
`economy.provider.mode` tells TriTown to stand aside and use it instead.

**A first town nobody can lose.** Every player without a town is given a founding credit once, on top of their
starting balance. Only founding a town with `/t new` can spend it: TriTown takes it off the price Towny charges, so a
new player who spends their balance by mistake can still found a town. Joining someone else's town gives it up, and
`/balance` shows it while you hold it.

**Resource regions in town.** Mark out part of a town — a mine, a farm, a wood, animal pens, a monster arena — and
its resources grow back on their own. Ore turns to bedrock when mined and returns, crops are replanted and grow through
their stages, trees regrow, and spawners keep animals and monsters about. Only the town's residents can gather there,
and they can even when the town denies them building, destroying and using everywhere else, which suits a starter town
new players join. Nothing else in a region can be changed by anyone, and nothing grows, burns or explodes there except
through TriTown. Everything is set up in game: draw the region with a wand, then click blocks, seeds and spawn eggs in
your inventory to add resources and spawners, and set each one's regrow time, the block that stands in while it is gone,
extra drops with their chances, and — for an ore vein that is different every time — what it may grow back as.
Players find their town's regions from the main menu or `/tt resources`, along with a tally of what they have gathered.

**A sidebar that follows you.** A scoreboard that changes with where you are standing: a new player without a town is
pointed at joining one, your own claims show your town's level, residents, land, bank, upkeep and any warning, another
town's claims show whose land it is and what the plot costs, and enemy territory says so. Values sit under headings that
group them, inside a shared header and footer carrying the server name and address. Which lines appear is set per board
in `config.yml`; the wording lives in the language files, so everyone reads it in their own language. Players turn it on
and off with `/tt scoreboard`, and it takes turns with Towny's own plot HUD rather than fighting it for the screen.

**A storage of your own.** Every player gets a storage reached from the menu, wherever they are standing: pages the
size of a large chest, used exactly like one, with a row of buttons beneath for turning pages, depositing everything
you already store some of, sorting, emptying a page into your inventory, and unpacking a shulker box or bundle straight
into it. Name your pages and give them icons, and see every page at once in an overview. The first pages are free and
more can be bought, each a little dearer than the last. It replaces vanilla containers: chests, barrels, shulker boxes
and ender chests are decoration now. They can still be placed, but nothing can be put in them — not even by a hopper —
one with items inside only lets them be taken out, and an empty one does not open. Nothing anyone stored is lost.

**Shops the server runs.** Admin shops, set up entirely in game: click an item in your own inventory to put it on the
shelf and it is sold exactly as you made it, custom name, enchantments and all. An entry can be sold, bought back, or
both, and priced in currency, items, or a mix of the two. Give it a stock that refills on a timer, a limit on how much
each player may buy or sell per day or per week, a permission node, or a requirement to be in a town or a nation — and give
town or nation members a discount while you are at it. Players reach a shop by clicking a
[FancyNpcs](https://modrinth.com/plugin/fancynpcs) NPC, and every sale is recorded in the transaction log and totalled
in a sales view.

**Trading, player to player.** Shift-right-click another player, or run `/trade <player>`, and once they agree you
both get the same table: sixteen stacks and any amount of money a side, yours on the left and theirs on the right.
Items leave your inventory the moment you put them up and are held by the trade, so what the other side is looking at
cannot be spent behind their back, and anything changing on the table clears both confirmations — nothing can be
swapped out after somebody has agreed to it. Everything comes straight back if either of you closes the menu, walks
away or disconnects.

**Fights worth gearing up for.** Mobs in the wild are levelled by where they spawn: every ring further from spawn is
harder, the Nether and the End start higher, and night and the deep add to it. Health and hits are in RPG numbers —
your 100 health is drawn over your ten hearts, a level-1 zombie has 100 and a level-30 one over 3,000 — and your
Defense, Damage, Strength and crits come from what you wear and hold. Some mobs spawn as elites and champions, with
affixes that make them fight differently, and the server's own mobs — the Gravewalker in its grave-gear, the Crypt
Ghoul, the Widow Stalker, the Voidstalker and more — take the place of wild mobs now and then, tougher, with abilities
of their own that they always warn you of — leaps, charges, volleys, meteors, webs, frost novas — and loot of their
own. Five bosses — the Grave Lord, the Broodmother, the Tide Tyrant, the Cinder Warlord and the Void Herald — are
summoned with sigils those mobs drop, fought in arenas with phases and boss bars, and share their loot, signature gear
among it, with everyone who helped. Wild mobs drop materials. At the Forge those become gear in ten tiers
and six rarities, which you upgrade, refine and reforge as you push further out. `/tt stats` shows your stats and where
each comes from, your health and Defense sit above your hotbar, and every hit you land floats its damage up from the
mob. At level 1 with vanilla gear it plays exactly like vanilla. Town claims are always level 1 and mobs from spawners
stay vanilla, so farms keep working, and players fighting each other are left entirely to vanilla.

**Server news.** Update notes, written in game and read from the main menu or with `/tt news`. A post is a title and
categories of short entries — a line or two about one change each, tagged New, Changed, Fixed, Removed or Note — read
straight off the menu, or as a book when it runs long. Players hear about what they have missed: a list of unread posts,
each a link, a moment after they join; a **News** button that glows and counts them; and, when a post is published, an
announcement to everyone online (or none, for a small post). Posts are written entirely in menus: start a draft,
type entries into chat one line after another (a leading `+`, `*`, `!`, `-` or `?` picks the tag), preview it as
players will see it, pin it, and publish it when it is ready. Every title and entry can be translated into each of the
server's languages, and players read their own.

**An admin panel.** `/tt admin` opens a menu that reads the server back to you. The economy section shows how much
currency exists and who holds it, what created it and what removed it — new players, shops, Towny, administrators,
storage pages, the Forge or another plugin — with the net drift per day, how unevenly wealth is spread, how fast money circulates, and a chart of
the window drawn as columns. Read any of it over the last day, week or month, or over everything on record. Every
shop's takings are in there too, next to the economy they act on, and every player's storage — how full it is, how
many pages were bought — which you can open and look through, online or not.

**English and Simplified Chinese.** Every message, menu and item TriTown shows is translated. By default each player
sees whichever of the two their Minecraft client is set to, and everyone else sees English. Set `language` in
`config.yml` to `en_US` or `zh_CN` to pick one for the whole server, or edit the files in `plugins/TriTown/lang/` to
reword anything — including adding a language of your own. Items are the one exception: the server cannot show an
item's text to each player differently, so gear and materials are written in one language for everyone, set by
`item-language` (by default the same as `language`, or English when that is `auto`).

Alongside it, TriTown provides the plugin framework and Towny integration that the server's custom town features are
built on.

## Requirements

| Dependency     | Version                             |
|:---------------|:------------------------------------|
| Paper          | 26.2+                               |
| Java           | 25+                                 |
| Towny          | 0.103.2.7+                          |
| Vault          | 1.7+                                |
| FancyNpcs      | 2.9+ — optional, for shop NPCs       |
| TownyMenu      | Optional — for the town shortcut     |
| Economy plugin | Not required — TriTown provides one |

TriTown is an addon: Towny and Vault must both be installed, or TriTown will not load. An economy plugin is optional —
install one only if you want it to supply the economy instead of TriTown, and set `economy.provider.mode` accordingly.
FancyNpcs is optional too: without it shops still work, they just cannot be opened by clicking an NPC. So is
TownyMenu: without it the main menu simply has no town button.

## Building

```powershell
./gradlew build
```

The compiled JAR is placed in `build/libs/`. Run `./gradlew copyPlugin` to copy it, along with the matching Towny jar,
into `run/plugins/` for the local test server, or `./gradlew startServer` to copy them and start the server. Before the
first start:

- download a Paper 26.2 jar from [papermc.io](https://papermc.io/downloads/paper) into `run/`;
- put Vault into `run/plugins/`;
- put [FancyNpcs](https://modrinth.com/plugin/fancynpcs) into `run/plugins/` as well, to test shop NPCs — only its API
  is published to Maven, so the plugin itself is not fetched by the build;
- accept the EULA in `run/eula.txt` after the first launch.

Prebuilt jars are attached to every [GitHub release](https://github.com/Trilleo/TriTown/releases).

**Your items stay yours.** Like Hypixel SkyBlock, the only way to give another player an item is a trade. What you drop,
mine, harvest, fish up or loot from a mob you killed can only be picked up by you. A furnace, hopper, brewing stand or
decorated pot belongs to whoever filled it until it is empty again, and so do item frames, armor stands and the saddle
on your horse. Nobody else can open, empty, break or blow them up. Death drops, and anything the world drops by
itself, are still anyone's.

## Commands

| Command                  | Description                           |
|:-------------------------|:--------------------------------------|
| `/tt help`               | List all available commands           |
| `/tt menu`               | Open the main menu                    |
| `/tt reload`             | Reload the configuration (OP only)    |
| `/balance [player]`      | Check your balance, or someone else's |
| `/pay <player> <amount>` | Send money to another player          |
| `/baltop [page]`         | List the richest accounts             |
| `/eco <action> …`        | Administer balances (OP only)         |
| `/tt scoreboard`         | Show or hide the sidebar              |
| `/trades`                | Open the server's global shop         |
| `/trade <player>`        | Ask another player to trade           |
| `/tt shop <action> …`    | Set up the server's shops (OP only)   |
| `/tt news`               | Read the server news                  |
| `/tt storage`            | Open your storage                     |
| `/tt admin [section]`    | Open the admin panel (OP only)        |
| `/tt protection <inspect/release>` | See or clear who owns what you look at (OP only) |
| `/tt stats [player]`     | See your combat stats, or another player's |
| `/tt mob <level/balance>` | Inspect mob levels and the balance (OP only) |
| `/tt item <give/list>`   | Hand out the server's own items and gear (OP only) |
| `/tt forge`              | Craft gear, and upgrade, refine, reforge or salvage it |
| `/tt resources`          | See your town's resource regions and what you have gathered |
| `/tt gather <action> …`  | Set up resource regions (OP only)     |

`/eco` takes `give`, `take` and `set` (`<player> <amount> [currency]`), `reset <player>` back to the starting balance,
`info <player>` for an account's details, `history [player]` to browse recorded transactions in a menu, and `flush` to
write changed accounts to disk immediately. Each action has its own permission, `tritown.economy.admin.<action>`;
viewing someone else's history additionally needs `tritown.economy.admin.history.others`.

`/tt shop` takes `list` for every shop, `create <id> [name]` to start one, `delete <id> confirm` to remove one,
`edit <id>` to change what it offers, `open <id> [player]` to open it for somebody, `bind <id> <npc>` and
`unbind <npc>` to put an NPC behind the counter, and `stats <id>` for what it has traded. Each action has its own
permission, `tritown.shop.admin.<action>`. Players have no shop command of their own — they click an NPC.

`/trade` also takes `accept [player]` and `deny [player]`, which the request message offers as buttons. Both players
have to be within `player-trades.distance` blocks of each other, and have to stay that close for as long as the menu
is open. There is no permission node: whether players may trade at all is `player-trades.enabled`.

`/tt news` opens the news; it also takes `open <id>` for one post (what the links in chat run), `readall` to mark
every post read, and `manage` to write them, which needs `tritown.news.manage` — as does the **Manage** button in the
news.

`/tt storage` opens your storage; there is no permission node, since whether it runs at all is `storage.enabled`.
Administrators with `tritown.storage.admin` also get `view <player>`, which opens anyone's storage — online or not — to
change, and `pages <player> <add|set> <amount>`, which hands out pages for free. `tritown.storage.bypass` lets a
builder place and fill the containers the storage replaces.

`/tt protection inspect` shows who owns the container, item frame, armor stand or mob you are looking at, and
`release` clears that claim; both need `tritown.protection.admin`. `tritown.protection.bypass` lets staff open, take
from and break anything a player has claimed.

`/tt stats` opens your combat stats — or, with a name, those of anyone online — each broken down by where it comes
from. It has no permission node; whether it runs at all is `combat.enabled`. `/tt mob level` says what level mobs
spawning where you stand would be, and why, and `/tt mob balance` prints what the balance in force makes of each level;
both need `tritown.mob.admin`. `/tt item give <player> <id> [amount]` hands out a material or essence, `/tt item give
<player> <id> [rarity]` a piece of gear, and `/tt item list` names them all; both need `tritown.item.admin`. `/tt forge`
opens the Forge and has no permission node; whether it runs is `combat.enabled`.

`/tt admin` opens the panel itself, and `economy`, `shops` or `storage` opens that section directly. Opening the panel
needs `tritown.admin`; the sections need `tritown.admin.economy`, `tritown.admin.shops` and `tritown.admin.storage` on
top of it.

Commands are sub-commands of `/tritown` (alias `/tt`) unless noted. The economy commands are registered as top-level
commands as well, which `economy.commands.top-level-aliases` turns off — they are then only reachable as
`/tt balance`, `/tt pay` and `/tt baltop`. If another plugin already owns one of those names it keeps it, and TriTown's
version stays available as `/tritown:balance` and so on.

## Configuration

| Key                                       | Default            | Description                                                                     |
|:------------------------------------------|:-------------------|:--------------------------------------------------------------------------------|
| `message-prefix`                          | —                  | MiniMessage prefix shown before plugin messages                                 |
| `language`                                | `auto`             | `auto` follows each player's client, or a language id such as `zh_CN`           |
| `item-language`                           | `auto`             | The one language gear and materials are written in; `auto` follows `language`   |
| `main-menu.item.enabled`                  | `true`             | Keep the menu item in the last hotbar slot of every player                      |
| `main-menu.item.material`                 | `NETHER_STAR`      | What the menu item is; any item works                                           |
| `economy.enabled`                         | `true`             | Turn the economy off entirely                                                   |
| `economy.provider.mode`                   | `auto`             | `internal`, `external` or `auto` — who supplies the Vault economy (restart)     |
| `economy.provider.defer-to`               | common eco plugins | Which installed plugins `auto` stands aside for                                 |
| `economy.currency.id`                     | `dollar`           | Stable key used in storage and commands                                         |
| `economy.currency.singular` / `.plural`   | `Dollar(s)`        | Display names                                                                   |
| `economy.currency.symbol`                 | `$`                | Short prefix shown before an amount                                             |
| `economy.currency.fractional-digits`      | `2`                | Digits kept after the decimal point (see below)                                 |
| `economy.currency.format`                 | `%symbol%%amount%` | Plain pattern other plugins print verbatim — no MiniMessage tags                |
| `economy.currency.rich-format`            | `<gold>…</gold>`   | MiniMessage pattern for TriTown's own messages                                  |
| `economy.starting-balance`                | `200.0`            | Balance granted on a player's first join                                        |
| `economy.balance-cap`                     | `1000000000.0`     | Largest balance an account may hold; `0` removes the cap                        |
| `economy.minimum-payment`                 | `0.01`             | Smallest amount a payment will accept                                           |
| `economy.allow-negative-balances`         | `false`            | Whether a withdrawal may take an account below zero                             |
| `economy.commands.top-level-aliases`      | `true`             | Register `/balance`, `/pay` and `/baltop` as their own commands (restart)       |
| `economy.commands.baltop-size`            | `10`               | Entries per page of `/baltop`                                                   |
| `economy.commands.baltop-include-towns`   | `false`            | List town and nation banks on `/baltop` alongside players                       |
| `economy.storage.type`                    | `json`             | Where balances are kept                                                         |
| `economy.storage.flush-interval`          | `60`               | Seconds between writes; a crash loses at most this long                         |
| `economy.storage.allow-rescale`           | `false`            | Convert balances when `fractional-digits` changes, instead of refusing to start |
| `economy.towny.delete-accounts-on-delete` | `true`             | Remove a town's or nation's account when Towny deletes it                       |
| `economy.history.enabled`                 | `true`             | Record every transaction to a log                                               |
| `economy.history.max-entries-per-account` | `100`              | Recent entries kept in memory per account                                       |
| `economy.history.retention-days`          | `30`               | How long rolled log files are kept; `0` keeps them forever                      |
| `economy.history.roll-size-mb`            | `16`               | Size at which the transaction log is rolled aside                               |
| `economy.history.time-format`             | `yyyy-MM-dd HH:mm` | How timestamps are shown in the history view                                    |
| `economy.stats.enabled`                   | `true`             | Keep the hourly figures the admin panel reads                                   |
| `economy.stats.retention-days`            | `30`               | How far back those figures reach; `0` keeps them forever                        |
| `shops.enabled`                           | `true`             | Turn shops off entirely                                                         |
| `shops.save-interval`                     | `60`               | Seconds between writing stock and sales figures; edits are saved immediately    |
| `shops.global-id`                         | `trades`           | The shop `/trades` opens; created empty if missing, and not deletable           |
| `shops.confirm-above`                     | `1000.0`           | Purchase total that asks for confirmation first; `0` never asks                 |
| `shops.sell-rate`                         | `0.5`              | What the editor suggests as a payout, as a fraction of the buy price            |
| `shops.discounts.<standing>`              | `0.0`              | Money off for `has-town`, `has-nation`, `is-mayor` or `is-king`                 |
| `player-trades.enabled`                   | `true`             | Turn player-to-player trading off entirely                                      |
| `player-trades.distance`                  | `10.0`             | How close two players must be to trade, and stay while the menu is open         |
| `player-trades.request-expiry`            | `60`               | Seconds an unanswered trade request stands                                      |
| `storage.enabled`                         | `true`             | Turn the storage and the container lock off (turning it on needs a restart)     |
| `storage.free-pages`                      | `2`                | Pages every player has without paying                                           |
| `storage.max-pages`                       | `27`               | The most pages a storage can reach, free ones included                          |
| `storage.price.base`                      | `500.0`            | What the first bought page costs                                                |
| `storage.price.multiplier`                | `1.5`              | How much dearer each bought page is than the one before                         |
| `storage.save-interval`                   | `30`               | Seconds between writing changed storages; closing one writes it at once         |
| `storage.lock-containers.enabled`         | `true`             | Make containers decoration: withdraw-only, and closed once empty                |
| `storage.lock-containers.<group>`         | `true`             | Each of `chests`, `shulker-boxes` and `ender-chest` on its own                 |
| `item-protection.enabled`                 | `true`             | Items only change hands through a trade                                         |
| `item-protection.disabled-worlds`         | `[]`               | Worlds where nothing is protected                                               |
| `item-protection.<part>`                  | `true`             | Each of `drops`, `actions`, `mob-loot`, `projectiles`, `containers`, `entities`, `explosion-guard` |
| `combat.enabled`                         | `true`             | Turn the combat layer off: hits stay vanilla and mobs are not levelled          |
| `combat.disabled-worlds`                  | `[]`               | Worlds where hits stay vanilla and mobs are not levelled                        |
| `combat.hud.action-bar`                   | `true`             | Show each player's health and Defense above the hotbar                          |
| `combat.hud.damage-indicators`            | `true`             | Float each hit's damage up from the mob it hit                                  |
| `combat.hud.indicator-limit`              | `64`               | The most damage indicators that may exist at once                               |
| `combat.speed-cap`                        | `30`               | The most Speed, in percent, that makes a player faster                          |
| `mobs.levels.night-bonus` / `.depth-bonus` | `2`               | Levels an Overworld mob gains at night, and below Y 0                           |
| `mobs.levels.<overworld/nether/end>`      | see below          | Each kind of world's `base`, `spawn-radius`, `ring-width`, `per-ring` and `cap` |
| `mobs.levels.worlds.<name>`               | —                  | Rings of a world's own, by name                                                 |
| `mobs.xp-per-level`                       | `0.02`             | Extra experience a mob drops per level above 1                                  |
| `mobs.nameplates`                         | `true`             | Show a levelled mob's level and health when a player looks at it                |
| `mobs.ranks.exempt`                       | bosses             | Kinds of mob that never spawn as elites or champions                            |
| `towns.founding-credit`                   | `100.0`            | Credit only `/t new` can spend, given once to players without a town; `0` is off |
| `gathering.enabled`                       | `true`             | Turn resource regions off entirely                                              |
| `gathering.max-volume`                    | `250000`           | The most blocks one region may cover                                            |
| `gathering.default-regrow-seconds`        | `60`               | How long a newly added resource takes to grow back                              |
| `gathering.depleted-blocks.<category>`    | `BEDROCK` / `AIR`  | What stands in for a harvested block, per category, unless the resource sets one |
| `gathering.spawner-range`                 | `48`               | How close a player has to be for a spawner to call up mobs                      |
| `gathering.effects`                       | `true`             | Particles and a chime when a block grows back                                   |
| `gathering.entry-titles`                  | `true`             | Show a region's name when a player walks in                                     |
| `gathering.save-interval`                 | `60`               | Seconds between writing harvested blocks to disk                                |
| `news.enabled`                            | `true`             | Turn the server news off entirely                                               |
| `news.join-message.enabled`               | `true`             | List a player's unread posts a moment after they join                           |
| `news.join-message.delay-seconds`         | `3`                | How long after joining                                                          |
| `news.join-message.preview`               | `3`                | How many unread titles that list names                                          |
| `news.announce.title` / `.chat`           | `true`             | Title and chat link everyone online gets when a post is announced               |
| `news.announce.sound`                     | a chime            | Sound played with the announcement; `""` for none                               |
| `news.max-entry-length`                   | `200`              | Most characters one entry may have, not counting colour tags                    |
| `scoreboard.enabled`                      | `true`             | Turn the sidebar off entirely                                                   |
| `scoreboard.refresh-interval`             | `2`                | Seconds between redraws of a sidebar nothing has changed on                     |
| `scoreboard.default-on`                   | `true`             | Whether a player who has never used `/tt scoreboard` sees one                   |
| `scoreboard.title-frame-interval`         | `10`               | Ticks between title frames; a single frame disables the animation               |
| `scoreboard.title`                        | four frames        | Translation keys for the title, cycled in order                                 |
| `scoreboard.header`                       | a divider          | Translation keys prepended to every board                                       |
| `scoreboard.footer`                       | divider, address   | Translation keys appended to every board                                        |
| `scoreboard.boards.<id>`                  | six boards         | A board's `priority`, `condition` and `lines` (translation keys)                |

Balances are stored as whole units of the smallest denomination, so `economy.currency.fractional-digits` fixes how every
balance on disk is read. Changing it once accounts exist stops the plugin with a message naming both values; set
`economy.storage.allow-rescale` to `true` to convert every balance once instead.

`economy.provider.*` and `economy.commands.top-level-aliases` only take effect on a restart — TriTown has to register
its economy with Vault, and its commands with the server, before either can be changed again. Everything else in the
table is applied by `/tt reload`.

### Shops

A shop is created with `/tt shop create <id>`, which opens its editor. Everything else is done in the menus:

- **Adding what it sells.** Click an item in your own inventory, or drag it over the menu. Nothing leaves your
  inventory — the item is copied, with every property it has, so a renamed and enchanted sword goes on the shelf as
  that exact sword. The stack size you click becomes the bundle: click a stack of 16 bread and one purchase is 16
  loaves. The bundle can be changed afterwards, and is not limited to a stack — 128 bread is handed over as two.
- **Arranging it.** Entries are shown to players in the order they are in the editor. Right-click one to pick it up,
  then click where it should go — including on another page — and it drops in front of whatever you clicked; two
  buttons send it to the front or the back of the shop instead. A whole shop can be put in order at once by name or by
  price, which replaces the arrangement you made by hand and asks before it does.
- **Pricing it.** An entry has a buy side and a sell side, and each may be switched on or off on its own. Either side
  can ask for money, for items, or for both at once. Money is typed in chat when you click the price; items are added
  by clicking them in your inventory, and the stack size is the quantity.
- **Reaching it.** A shop normally stands behind an NPC. One does not: the shop named by `shops.global-id`
  (`trades` by default) opens from anywhere with `/trades`, for the goods the server always trades. It is created
  empty on first start, is edited like any other shop, and cannot be deleted while it is the one `/trades` opens.
- **Buying it.** A player left-clicks an entry to buy one purchase of it, and shift-left-clicks anything that stacks to
  pick an amount instead — 1, 8, 16, 32 or 64, priced at the entry's own rate, so eight of something sold sixteen at a
  time costs half. An amount they cannot take is greyed out with the reason rather than refusing after the click. Right
  -click sells one purchase back, and shift-right-click sells everything they are carrying.
- **Limiting it.** *Stock* is shared by everybody and refills to full on a timer. A *limit* is per player and resets
  daily, weekly, or never; buying and selling have one each, and they are counted separately. All of them are counted
  in items rather than in purchases — a limit of 64 on an entry that sells 16 at a time is four purchases — and all of
  them are optional. An entry with none is unlimited, which is what an admin shop usually wants.
- **Locking it.** A shop, and each entry inside it, can require a permission node or a standing in Towny — being in a
  town, being without one, being in a nation, being a mayor or being a king. A locked entry shows the reason by
  default, or can be hidden entirely.

`shops.discounts` takes money off for players who have earned it. Discounts do not stack: a mayor whose nation also has
a rate pays the better of the two, and the menu shows the old price struck through beside the new one. An individual
entry can opt out.

Players never type a shop command. Bind an NPC with `/tt shop bind <id> <npc>` and clicking it opens the shop. The
binding is stored against the NPC itself rather than its name, so renaming it in FancyNpcs changes nothing, and an NPC
opens one shop at a time — binding it again moves it.

The goods a shop sells are created and the money paid for them leaves the economy, so a shop is a sink, a faucet, or
both depending on how you price it. `/tt shop stats <id>` shows which, per entry and in total. Every trade is recorded
in the transaction log and appears in `/eco history` as a shop movement naming the shop.

Shops live in `plugins/TriTown/shops/shops.json`, written atomically with a `.bak` copy beside it. A shop you edit is
written straight away; stock levels and sales figures are written every `shops.save-interval` seconds, so a crash costs
at most that long of counters and never a shop.

### Storage

Open your storage from the main menu or with `/tt storage`. Each page is five rows of ordinary slots, and you move
items in and out of them just as you would a chest. The row beneath holds the buttons:

- **Previous** and **Next** turn the page. On your last page, **Next** offers the next page for sale instead, with its
  price, and asks before charging you. Each page bought costs `storage.price.multiplier` times the one before.
- **All pages** shows every page at once — its name, how full it is and what it mostly holds — and opens any of them.
- **Quick deposit** stores everything in your main inventory that your storage already holds some of. Your hotbar is
  left alone.
- **Sort** merges and orders the page; shift-click sorts every page.
- **Take page** moves as much of the page as fits into your inventory.
- **Unpack** empties a shulker box or bundle into your storage: pick one up and click the button with it. A full box
  or bundle cannot be stored as it is, so this is how its contents come home.
- **Page settings** renames the page (typed in chat) or gives it an icon (click any item in your inventory).

Chests, trapped chests, barrels, shulker boxes, ender chests, chest minecarts and chest boats are decoration. They can
still be placed, with a warning, but nothing can be put in them: one with items inside opens only to take things out,
and an empty one does not open at all. Hoppers can still empty them but not fill them. Each group can be left alone
under `storage.lock-containers`.

Storages live in `plugins/TriTown/storage/<uuid>.json`, one per player, written atomically with a `.bak` copy beside
it. A storage is written when it is closed, when its page is turned, every `storage.save-interval` seconds while it
is open, and on a clean shutdown. If a file and its backup are both unreadable, that player's storage stays closed —
and the file untouched — rather than opening empty.

### Player trades

Shift-right-click the other player, or run `/trade <player>`. They get a request with **Accept** and **Deny** buttons,
and nothing opens until they take it. Both of you have to be standing close by, and have to stay there.

In the menu, click an item in your inventory to put it up — right-click puts up a single one — and click it again in
the menu to take it back. The gold ingot is your money: left-click adds, right-click takes off, hold shift for ten
times as much, and press **Q** to type an exact amount in chat. You can never put up more than you actually have.

Anything either of you changes clears both confirmations and greys the button for a moment, so nothing can be swapped
out after the other person has agreed to it. When you have both confirmed, the items change hands and any difference
in money is paid across in one payment, recorded in the transaction log like any other.

Closing the menu calls the trade off and everything goes straight back. So does walking too far apart, disconnecting,
or the server stopping.

### Combat

Every player and every mob has an RPG health pool drawn over their vanilla hearts. The pool is always exactly as full
as the hearts are, so potions, regeneration, totems and deaths all work as they always have; only the numbers change.
Hits between a player and a mob, or between two mobs, are worked out from stats:

- **Health** is your pool, 100 to start. **Defense** takes a share off every mob's hit — 100 halves it. **Damage**
  comes from your weapon, **Strength** adds to every hit in percent (the Strength effect gives some), and **Crit
  Chance** and **Crit Damage** decide how often a hit lands harder and by how much; jump attacks add to your crit
  chance. **Speed** moves you faster, **Vitality** makes regeneration and healing potions give back more, and **Magic
  Find** makes rare drops likelier. Vanilla armor and weapons count, and so does gear.
- None of it applies between players. A hit from one player to another, or from a player's pet, is left to vanilla, and
  so is everything the world does to you: falling, lava, drowning. The one stat players feel is Speed, which is capped
  (`combat.speed-cap`) short of a Speed II potion.

Mobs are levelled by where they spawn. Each kind of world has a spawn area where mobs stay at its base level, and
rings around it, each harder than the one inside:

| World     | Base | Spawn area         | Each ring            | Cap |
|:----------|:-----|:-------------------|:---------------------|:----|
| Overworld | 1    | 300 blocks         | 500 blocks, +2 levels | 30  |
| Nether    | 4    | 64 blocks          | 64 blocks, +2 levels  | 45  |
| End       | 8    | the main island (1,000) | 500 blocks, +3 levels | 60 |

Night and being below Y 0 each add 2 in the Overworld. A level grows a mob's health and hits, and the experience it
drops. Town claims are always level 1, and so is every mob from a spawner, an egg or a command, so farms keep working as
they do in vanilla. Levels are kept with the mob, so they survive restarts.

From level 5 a wild mob can spawn as an **elite** (★), and from level 15 as a **champion** (★★): several times the
health, harder hits, and one to three affixes, named on the mob and shown by the particles around it — Armored,
Frenzied, Vampiric, Enraged, Molten, Frostbound, Venomous, Volatile, Summoner, Blinking or Warded. Champions glow.
Bosses such as the Warden never spawn ranked (`mobs.ranks.exempt`).

From level 3, wild mobs drop **materials** on top of their usual loot — Grave Dust from zombies, Ember Cores from
blazes, Void Fragments from endermen, fourteen in all — and elites and champions always do, along with **essence**
whose grade follows their level. A champion now and then drops a finished piece of gear. Magic Find makes the chancier
drops likelier; Looting gives some. Only a mob that spawned in the wild drops any of it, and only when players dealt at
least half its damage, so a trap or a lava pit earns nothing. What drops is the killer's, like the rest of the mob's
loot. Materials are inert: they look like the vanilla item they are modelled on, but cannot be crafted with, placed or
used. Mobs never drop money.

### Custom mobs

The server has mobs of its own, defined in `plugins/TriTown/content/bestiary.yml`. Now and then one takes the place of
a wild mob of its kind — only ever a wild one, so never in a town, from a spawner or from an egg:

| Mob               | Is a            | Lives                          | Fights with                       |
|:------------------|:----------------|:-------------------------------|:----------------------------------|
| Gravewalker       | zombie          | the Overworld, levels 3–14     | Leap; wears the Gravewalker set   |
| Crypt Ghoul       | husk            | deserts, 6–16                  | Leap; small, fast and Vampiric    |
| Bonecaller Sentry | skeleton        | the Overworld, 6–16            | Volley; wears the Bonecaller set  |
| Frost Revenant    | stray           | the cold, 10–22                | Frost Nova; a Frostbite Edge      |
| Widow Stalker     | spider          | above Y 40, 12–24              | Ensnare, Leap                     |
| Silkweaver        | spider          | below Y 0, 14–26               | Miasma; Venomous                  |
| Hexbinder         | witch           | swamps, 12–30                  | Drain, Ensnare                    |
| Tidecaller        | drowned         | the seas, 16–30                | Hook; a Tidecaller Trident        |
| Crimson Warhog    | hoglin          | the Nether, 18–36              | Charge                            |
| Pyromancer        | blaze           | the Nether, 22–40              | Fireball, Meteor; Molten          |
| Ashen Legionnaire | wither skeleton | the Nether, 30–45              | Charge, Slam; the Ashen set       |
| Voidstalker       | enderman        | the End, 30–60                 | Hook, Drain; Blinking             |
| Starborn Sentinel | enderman        | the outer End, 48–60           | Storm, Bulwark; Warded            |

Each wears its own name on its nameplate, is tougher than its kind, and may still be an elite or a champion. Its
abilities always warn you with particles and sound before they land, break no blocks, and only ever hurt players. Its
costume is only a look: what it wears gives it no armor and never drops, though some drop a finished piece of the gear
they wear now and then. They drop more of their kind's materials, and four drop **trophies** — Hollow Marrow, Abyssal
Scales, Warhog Hide and Null Shards — that the armor of tiers 2, 4, 6 and 8 is forged from. Voidstalkers and Starborn
Sentinels are where Rift Shells come from.

`mobs.custom.enabled` turns them off. `/tt mob spawn <kind> [level]` calls one up to look at (it drops nothing extra),
and `/tt mob kinds` lists them.

### Bosses

Five bosses are summoned by players, each with a **sigil** that the custom mobs of its region now and then drop.
Right-click one where its boss answers and, after a short ritual, the boss rises a few blocks in front of you:

| Boss           | Level | Summoned                  | Its sigil drops from               | Its signature gear                   |
|:---------------|:------|:--------------------------|:-----------------------------------|:-------------------------------------|
| Grave Lord     | 12    | the Overworld's wilds     | Gravewalkers, Crypt Ghouls         | Gravelord's Crown, Sepulcher Cleaver |
| Broodmother    | 24    | underground, below Y 40   | Widow Stalkers, Silkweavers        | Broodfang, Widowmantle               |
| Tide Tyrant    | 30    | from the Overworld's water | Tidecallers, Hexbinders           | Tyrant's Harpoon, Crown of Tides     |
| Cinder Warlord | 42    | the Nether                | Pyromancers, Ashen Legionnaires    | Warlord's Brand, Cinder Aegis        |
| Void Herald    | 60    | the End                   | Voidstalkers, Starborn Sentinels   | Eclipse Scythe, Herald's Crown       |

A boss fights in an arena around where it rose, glowing, with a boss bar for everyone nearby. As its health falls it
enters new phases — calling minions, gaining affixes, learning abilities — and it gives up and leaves if nobody stays in
its arena. It is built for a group: alone, in gear of its tier, it takes over a hundred swings.

Everyone who dealt at least a tenth of its health gets **their own share** of its loot, with their own Magic Find,
dropped where it fell for them alone (or straight into their inventory if they are far away): its region's materials,
trophies and essence, perhaps a finished piece of its tier, and perhaps its **signature gear**, which only bosses drop
and which can be mythic. Sigils never work in a town, and a boss never teleports, despawns or leaves its world.

`mobs.bosses.enabled` turns summoning off, and `mobs.bosses.announce-range` sets who hears of a boss. `/tt mob bosses`
shows each boss against one player in a kit of its tier, and `/tt mob spawn <boss>` calls one up to test.

### The bestiary

`/tt bestiary`, or **Bestiary** in the main menu, keeps a page for every custom mob and boss. Until you slay one, its
page is blank but for where to look. After that it shows what it is, where it lives or how it is summoned, its
abilities and affixes, how many you have slain — with a star at 10, 100 and 1,000 — and which of its own loot you have
found so far.

### Gear and the Forge

**Gear** comes in ten tiers, each made for mobs up to six times its level — tier 5 is for level 25 to 30 — and in six
rarities, common to mythic. A tier is worth about twice the one before; a rarity step is worth far less, so the tier is
what you climb and the rarity what you chase. Every piece of a slot and tier is worth the same, however its stats are
shared out. Gear never wears out. Between players, a piece is exactly the vanilla item it is made of — a tier-7 blade
forged from a netherite sword fights players as a netherite sword — and its tooltip says so.

The starting set is an armor set for every tier — Gravewalker, Bonecaller, Widowsilk, Tidecaller, Emberforged,
Warhide, Ashen Knight, Riftwalker, Voidstride and Starfall — and a weapon for every tier, bows and a crossbow and a
trident among them. The Bonecaller, Tidecaller, Warhide and Riftwalker sets take trophies that only custom mobs drop.

**The Forge** (`/tt forge`, or the main menu) crafts a piece from materials, essence and money, at a random rarity. Hold
a piece in your main hand and the Forge can also:

- **Upgrade** it: up to five stars, each worth 4% more stats, each costing more than the last;
- **Refine** it: one step of rarity, up to legendary — mythic only ever drops;
- **Reforge** it: a new named reforge, such as Sharp or Titanic, adding a little of its own;
- **Salvage** it: break it down for good into essence.

Every step asks first. Upgrading and refining take the essence of the piece's tier, so improving gear means fighting at
its level. The money the Forge takes leaves the economy, and the admin panel shows it under **The Forge**.

Everything is tuned in files of its own in `plugins/TriTown/content/`, which `/tt reload` reads again:

- `balance.yml` — what everything is worth: the lens (RPG health per vanilla half-heart, 5), your base stats, how much
  a level grows a mob, ranks and affixes, what vanilla gear counts as, gear's budget, and what the Forge charges.
- `mobs.yml` — which mob drops which material, and how often.
- `items.yml` — the materials and essence: how each looks and how rare it is.
- `gear.yml` — every piece of gear: its slot, tier, base item, look, how it shares out its stats, and its recipe; and
  the reforges. Retuning a piece reaches every copy already out there.
- `bestiary.yml` — the custom mobs and bosses: what each is, where it lives or is summoned, how much tougher than its
  kind, its affixes, abilities and phases, what it wears and what it drops; and how boss fights and their loot go.
  Retuning one reaches every one already out there. What each ability does is in `balance.yml`.

Names are in the language files under `item` and `gear`. `/tt mob balance` shows what your numbers make of each level —
a zombie against a full kit of the tier made for it — before anyone fights. A value that cannot be used falls back to
the bundled default, and the console says which.

### The sidebar

Each board under `scoreboard.boards` has a `priority`, a `condition`, and a list of `lines`. A player sees the
highest-priority board whose condition matches, so one player gets different information depending on where they are
standing. The conditions are `always`, `no-town`, `has-town`, `no-nation`, `has-nation`, `in-wilderness`,
`in-own-town`, `in-own-plot`, `in-other-town`, `in-ally-town`, `in-enemy-town` and `town-has-warning`.

A line names a translation key rather than carrying text, so you arrange the layout here and the wording stays in
`plugins/TriTown/lang/`. An empty entry (`""`) is a blank spacer. Values are written into a line as `%town_bank%`,
`%plot_owner%`, `%balance%` and so on — `config.yml` lists every available marker beside the block.

Every board is wrapped in the shared `header` and `footer`, so the frame around the sidebar is written once instead of
being repeated in each board. Minecraft shows at most 15 lines; the header and footer count towards that, leaving 12 per
board by default. A board that declares more than fits loses its own last lines, never the frame, and says so in the
console.

The sidebar and Towny's `/towny plot perm hud` are mutually exclusive: turning either on puts the other away, and
TriTown's returns once Towny's is switched off. Everything under `scoreboard` is applied by `/tt reload`.

## Translations

TriTown ships English (`en_US`) and Simplified Chinese (`zh_CN`). Both are copied into `plugins/TriTown/lang/` the first
time the plugin starts, and `/tt reload` re-reads them.

- `language: auto` (the default) gives each player the file matching their Minecraft client language, falling back to
  `en_US`. A client set to a language TriTown does not have but that shares a prefix — `zh_TW`, say — gets the closest
  match (`zh_CN`).
- Setting `language` to a file's id, such as `zh_CN`, shows that one to everybody.
- Edit either file to change any wording. Keep every `{placeholder}` — TriTown fills those in — and note that the values
  use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. A key you delete falls back to the
  bundled copy, so nothing breaks if you remove a line.
- To add a language, drop a `<id>.yml` of your own beside them; anything it does not define falls back to English.

The `money.error.*` entries are the exception: they are plain text with no formatting, because they also travel to other
plugins as Vault's error message and are printed verbatim.

## Running the Economy

### Choosing who supplies it

TriTown supplies the Vault economy by default, and Towny picks it up automatically. On startup the console says which
economy won:

```
[TriTown] Registered TriTown as a Vault economy provider (mode=AUTO, priority=Low)
[TriTown] TriTown is supplying the server economy (Towny sees: TriTown via Vault)
```

If you already run an economy plugin, leave `economy.provider.mode` on `auto` — TriTown stands aside when a known
economy plugin is installed. Set it to `external` to always stand aside, or `internal` to always win.

Two things about this are worth knowing:

- **It is decided at startup.** TriTown has to register with Vault before Towny starts, and Towny chooses an economy
  exactly once, so `/tt reload` cannot change it. Restart the server.
- **A plugin that registers an economy after TriTown will be ignored by Towny.** TriTown warns in the console when this
  happens. Remove one of the two plugins and restart, or run `/townyadmin eco convert modern` to make Towny look again.

### Where the data lives

| File                                        | What it is                                                        |
|:--------------------------------------------|:------------------------------------------------------------------|
| `plugins/TriTown/economy/accounts.json`     | Every balance                                                     |
| `plugins/TriTown/economy/accounts.json.bak` | The previous copy, used automatically if the main file is damaged |
| `plugins/TriTown/economy/transactions.log`  | The transaction record                                            |

Balances are written every `economy.storage.flush-interval` seconds, whenever an administrator changes one, when a
player logs out, and on a clean shutdown. **A clean shutdown is the important one** — `/stop` writes everything, but a
crashed or killed process does not, so a crash loses at most one flush interval of activity. Lower the interval if that
matters more to you than the extra writes.

TriTown would rather not start than start with the wrong money. It refuses to start if `accounts.json` and its backup
are both unreadable, if the data was written by a newer version of TriTown, or if `economy.currency.fractional-digits`
no longer matches what the data was written with. Each of those says what to do in the console message.

### Moving from another economy plugin

There is no automatic import. Either keep the other plugin and set `economy.provider.mode` to `external`, or move the
balances across once with `/eco set <player> <amount>` and then remove it. Do this with the server quiet, and take a
copy of `plugins/TriTown/economy/` first.

## Developer Documentation

Full development guides are in the `docs/` directory:

- [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) — How to add commands, listeners, GUIs, tasks, items, and recipes,
  translate every string, use the configuration and data storage, and build on Towny and the Vault economy.
- [UTILITY_GUIDE.md](docs/UTILITY_GUIDE.md) — Reference for the utility helpers (`itemStack` DSL, `Lang`,
  `EconomyUtil`, `MessageUtil`, `ChatPrompt`, `CountdownUtil`, `TeamUtil`, `TagUtil`, `PDCUtil`, `GameRuleUtil`,
  `LoreUtil`).
- [COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md) — Commit message conventions.
- [RELEASING.md](docs/RELEASING.md) — Writing the changelog and publishing a release.

For AI-assisted development, see [CLAUDE.md](CLAUDE.md).

<h1 align="center">
  TriTown
</h1>

<p align="center">
  A <a href="https://github.com/TownyAdvanced/Towny">Towny</a> addon with custom town features for the Trilleo server.
</p>

---

## Features

TriTown is in early development; see the [change log](CHANGELOG.md) for what has shipped.

**A built-in economy.** TriTown supplies the server's Vault economy itself, so Towny gets working player wallets and
town and nation banks without a separate economy plugin such as EssentialsX. Balances are stored as whole units of the
smallest denomination, so they never drift, and they are written to disk atomically with a backup copy. The currency,
starting balance, balance cap and formatting are all configurable. If you would rather keep another economy plugin,
`economy.provider.mode` tells TriTown to stand aside and use it instead.

**English and Simplified Chinese.** Every message, menu and item TriTown shows is translated. By default each player
sees whichever of the two their Minecraft client is set to, and everyone else sees English. Set `language` in
`config.yml` to `en_US` or `zh_CN` to pick one for the whole server, or edit the files in `plugins/TriTown/lang/` to
reword anything — including adding a language of your own.

Alongside it, TriTown provides the plugin framework and Towny integration that the server's custom town features are
built on.

## Requirements

| Dependency     | Version                                  |
|:---------------|:-----------------------------------------|
| Paper          | 26.2+                                    |
| Java           | 25+                                      |
| Towny          | 0.103.2.7+                               |
| Vault          | 1.7+                                     |
| Economy plugin | Not required — TriTown provides one      |

TriTown is an addon: Towny and Vault must both be installed, or TriTown will not load. An economy plugin is optional —
install one only if you want it to supply the economy instead of TriTown, and set `economy.provider.mode` accordingly.

## Building

```powershell
./gradlew build
```

The compiled JAR is placed in `build/libs/`. Run `./gradlew copyPlugin` to copy it, along with the matching Towny jar,
into `run/plugins/` for the local test server, or `./gradlew startServer` to copy them and start the server. Before the
first start:

- download a Paper 26.2 jar from [papermc.io](https://papermc.io/downloads/paper) into `run/`;
- put Vault into `run/plugins/`;
- accept the EULA in `run/eula.txt` after the first launch.

Prebuilt jars are attached to every [GitHub release](https://github.com/Trilleo/TriTown/releases).

## Commands

| Command            | Description                         |
|:-------------------|:------------------------------------|
| `/tt help`         | List all available commands         |
| `/tt reload`       | Reload the configuration (OP only)  |
| `/balance [player]`| Check your balance, or someone else's |
| `/pay <player> <amount>` | Send money to another player  |
| `/baltop [page]`   | List the richest accounts           |
| `/eco <action> …`  | Administer balances (OP only)       |

`/eco` takes `give`, `take` and `set` (`<player> <amount> [currency]`), `reset <player>` back to the starting balance,
`info <player>` for an account's details, `history [player]` to browse recorded transactions in a menu, and `flush` to
write changed accounts to disk immediately. Each action has its own permission, `tritown.economy.admin.<action>`;
viewing someone else's history additionally needs `tritown.economy.admin.history.others`.

Commands are sub-commands of `/tritown` (alias `/tt`) unless noted. The economy commands are registered as top-level
commands as well, which `economy.commands.top-level-aliases` turns off — they are then only reachable as
`/tt balance`, `/tt pay` and `/tt baltop`. If another plugin already owns one of those names it keeps it, and
TriTown's version stays available as `/tritown:balance` and so on.

## Configuration

| Key                                    | Default          | Description                                                                 |
|:---------------------------------------|:-----------------|:-----------------------------------------------------------------------------|
| `message-prefix`                       | —                | MiniMessage prefix shown before plugin messages                             |
| `language`                             | `auto`           | `auto` follows each player's client, or a language id such as `zh_CN`        |
| `economy.enabled`                      | `true`           | Turn the economy off entirely                                               |
| `economy.provider.mode`                | `auto`           | `internal`, `external` or `auto` — who supplies the Vault economy (restart) |
| `economy.provider.defer-to`            | common eco plugins | Which installed plugins `auto` stands aside for                           |
| `economy.currency.id`                  | `dollar`         | Stable key used in storage and commands                                     |
| `economy.currency.singular` / `.plural`| `Dollar(s)`      | Display names                                                               |
| `economy.currency.symbol`              | `$`              | Short prefix shown before an amount                                         |
| `economy.currency.fractional-digits`   | `2`              | Digits kept after the decimal point (see below)                             |
| `economy.currency.format`              | `%symbol%%amount%` | Plain pattern other plugins print verbatim — no MiniMessage tags           |
| `economy.currency.rich-format`         | `<gold>…</gold>` | MiniMessage pattern for TriTown's own messages                              |
| `economy.starting-balance`             | `100.0`          | Balance granted on a player's first join                                    |
| `economy.balance-cap`                  | `1000000000.0`   | Largest balance an account may hold; `0` removes the cap                    |
| `economy.minimum-payment`              | `0.01`           | Smallest amount a payment will accept                                       |
| `economy.allow-negative-balances`      | `false`          | Whether a withdrawal may take an account below zero                         |
| `economy.commands.top-level-aliases`   | `true`           | Register `/balance`, `/pay` and `/baltop` as their own commands (restart)   |
| `economy.commands.baltop-size`         | `10`             | Entries per page of `/baltop`                                               |
| `economy.commands.baltop-include-towns`| `false`          | List town and nation banks on `/baltop` alongside players                   |
| `economy.storage.type`                 | `json`           | Where balances are kept                                                     |
| `economy.storage.flush-interval`       | `60`             | Seconds between writes; a crash loses at most this long                     |
| `economy.storage.allow-rescale`        | `false`          | Convert balances when `fractional-digits` changes, instead of refusing to start |
| `economy.towny.delete-accounts-on-delete` | `true`        | Remove a town's or nation's account when Towny deletes it                   |
| `economy.history.enabled`              | `true`           | Record every transaction to a log                                           |
| `economy.history.max-entries-per-account` | `100`         | Recent entries kept in memory per account                                   |
| `economy.history.retention-days`       | `30`             | How long rolled log files are kept; `0` keeps them forever                  |
| `economy.history.roll-size-mb`         | `16`             | Size at which the transaction log is rolled aside                           |
| `economy.history.time-format`          | `yyyy-MM-dd HH:mm` | How timestamps are shown in the history view                              |

Balances are stored as whole units of the smallest denomination, so `economy.currency.fractional-digits` fixes how
every balance on disk is read. Changing it once accounts exist stops the plugin with a message naming both values;
set `economy.storage.allow-rescale` to `true` to convert every balance once instead.

`economy.provider.*` and `economy.commands.top-level-aliases` only take effect on a restart — TriTown has to register
its economy with Vault, and its commands with the server, before either can be changed again. Everything else in the
table is applied by `/tt reload`.

## Translations

TriTown ships English (`en_US`) and Simplified Chinese (`zh_CN`). Both are copied into `plugins/TriTown/lang/` the
first time the plugin starts, and `/tt reload` re-reads them.

- `language: auto` (the default) gives each player the file matching their Minecraft client language, falling back to
  `en_US`. A client set to a language TriTown does not have but that shares a prefix — `zh_TW`, say — gets the closest
  match (`zh_CN`).
- Setting `language` to a file's id, such as `zh_CN`, shows that one to everybody.
- Edit either file to change any wording. Keep every `{placeholder}` — TriTown fills those in — and note that the
  values use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. A key you delete falls back to
  the bundled copy, so nothing breaks if you remove a line.
- To add a language, drop a `<id>.yml` of your own beside them; anything it does not define falls back to English.

The `money.error.*` entries are the exception: they are plain text with no formatting, because they also travel to
other plugins as Vault's error message and are printed verbatim.

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
- **A plugin that registers an economy after TriTown will be ignored by Towny.** TriTown warns in the console when
  this happens. Remove one of the two plugins and restart, or run `/townyadmin eco convert modern` to make Towny look
  again.

### Where the data lives

| File                                  | What it is                                                         |
|:--------------------------------------|:---------------------------------------------------------------------|
| `plugins/TriTown/economy/accounts.json` | Every balance                                                      |
| `plugins/TriTown/economy/accounts.json.bak` | The previous copy, used automatically if the main file is damaged |
| `plugins/TriTown/economy/transactions.log` | The transaction record                                           |

Balances are written every `economy.storage.flush-interval` seconds, whenever an administrator changes one, when a
player logs out, and on a clean shutdown. **A clean shutdown is the important one** — `/stop` writes everything, but a
crashed or killed process does not, so a crash loses at most one flush interval of activity. Lower the interval if
that matters more to you than the extra writes.

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
  `EconomyUtil`, `MessageUtil`, `CountdownUtil`, `TeamUtil`, `TagUtil`, `PDCUtil`, `GameRuleUtil`, `LoreUtil`).
- [COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md) — Commit message conventions.
- [RELEASING.md](docs/RELEASING.md) — Writing the changelog and publishing a release.

For AI-assisted development, see [CLAUDE.md](CLAUDE.md).

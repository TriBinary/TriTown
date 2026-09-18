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
`info <player>` for an account's details, and `flush` to write changed accounts to disk immediately. Each action has
its own permission, `tritown.economy.admin.<action>`.

Commands are sub-commands of `/tritown` (alias `/tt`) unless noted. The economy commands are registered as top-level
commands as well, which `economy.commands.top-level-aliases` turns off — they are then only reachable as
`/tt balance`, `/tt pay` and `/tt baltop`. If another plugin already owns one of those names it keeps it, and
TriTown's version stays available as `/tritown:balance` and so on.

## Configuration

| Key                                    | Default          | Description                                                                 |
|:---------------------------------------|:-----------------|:-----------------------------------------------------------------------------|
| `message-prefix`                       | —                | MiniMessage prefix shown before plugin messages                             |
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

## Developer Documentation

Full development guides are in the `docs/` directory:

- [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) — How to add commands, listeners, GUIs, tasks, items, and recipes, use
  the configuration and data storage, and build on Towny and the Vault economy.
- [UTILITY_GUIDE.md](docs/UTILITY_GUIDE.md) — Reference for the utility helpers (`itemStack` DSL, `EconomyUtil`,
  `MessageUtil`, `CountdownUtil`, `TeamUtil`, `TagUtil`, `PDCUtil`, `GameRuleUtil`, `LoreUtil`).
- [COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md) — Commit message conventions.
- [RELEASING.md](docs/RELEASING.md) — Writing the changelog and publishing a release.

For AI-assisted development, see [CLAUDE.md](CLAUDE.md).

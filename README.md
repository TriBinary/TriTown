<h1 align="center">
  TriTown
</h1>

<p align="center">
  A <a href="https://github.com/TownyAdvanced/Towny">Towny</a> addon with custom town features for the Trilleo server.
</p>

---

## Features

TriTown is in early development. It currently provides the plugin framework, Towny integration, and Vault economy
support that the server's custom town features are built on; see the [change log](CHANGELOG.md) for what has shipped.

## Requirements

| Dependency     | Version                  |
|:---------------|:-------------------------|
| Paper          | 26.2+                    |
| Java           | 25+                      |
| Towny          | 0.103.2.7+               |
| Vault          | 1.7+                     |
| Economy plugin | Any Vault-compatible one |

TriTown is an addon: Towny and Vault must be installed on the server, along with an economy plugin that Vault supports
(for example EssentialsX), or TriTown will not load.

## Building

```powershell
./gradlew build
```

The compiled JAR is placed in `build/libs/`. Run `./gradlew copyPlugin` to copy it, along with the matching Towny jar,
into `run/plugins/` for the local test server, or `./gradlew startServer` to copy them and start the server. Before the
first start:

- download a Paper 26.2 jar from [papermc.io](https://papermc.io/downloads/paper) into `run/`;
- put Vault and an economy plugin (for example EssentialsX) into `run/plugins/`;
- accept the EULA in `run/eula.txt` after the first launch.

Prebuilt jars are attached to every [GitHub release](https://github.com/Trilleo/TriTown/releases).

## Commands

All commands are sub-commands of `/tritown` (alias `/tt`).

| Command      | Description                        |
|:-------------|:-----------------------------------|
| `/tt help`   | List all available commands        |
| `/tt reload` | Reload the configuration (OP only) |

## Configuration

| Key                                    | Default          | Description                                                                 |
|:---------------------------------------|:-----------------|:-----------------------------------------------------------------------------|
| `message-prefix`                       | —                | MiniMessage prefix shown before plugin messages                             |
| `economy.enabled`                      | `true`           | Turn the economy off entirely                                               |
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
| `economy.storage.type`                 | `json`           | Where balances are kept                                                     |
| `economy.storage.flush-interval`       | `60`             | Seconds between writes; a crash loses at most this long                     |
| `economy.storage.allow-rescale`        | `false`          | Convert balances when `fractional-digits` changes, instead of refusing to start |

Balances are stored as whole units of the smallest denomination, so `economy.currency.fractional-digits` fixes how
every balance on disk is read. Changing it once accounts exist stops the plugin with a message naming both values;
set `economy.storage.allow-rescale` to `true` to convert every balance once instead.

## Developer Documentation

Full development guides are in the `docs/` directory:

- [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) — How to add commands, listeners, GUIs, tasks, items, and recipes, use
  the configuration and data storage, and build on Towny and the Vault economy.
- [UTILITY_GUIDE.md](docs/UTILITY_GUIDE.md) — Reference for the utility helpers (`itemStack` DSL, `EconomyUtil`,
  `MessageUtil`, `CountdownUtil`, `TeamUtil`, `TagUtil`, `PDCUtil`, `GameRuleUtil`, `LoreUtil`).
- [COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md) — Commit message conventions.
- [RELEASING.md](docs/RELEASING.md) — Writing the changelog and publishing a release.

For AI-assisted development, see [CLAUDE.md](CLAUDE.md).

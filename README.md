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

| Key              | Default | Description                                     |
|:-----------------|:--------|:------------------------------------------------|
| `message-prefix` | —       | MiniMessage prefix shown before plugin messages |

## Developer Documentation

Full development guides are in the `docs/` directory:

- [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) — How to add commands, listeners, GUIs, tasks, items, and recipes, use
  the configuration and data storage, and build on Towny and the Vault economy.
- [UTILITY_GUIDE.md](docs/UTILITY_GUIDE.md) — Reference for the utility helpers (`itemStack` DSL, `EconomyUtil`,
  `MessageUtil`, `CountdownUtil`, `TeamUtil`, `TagUtil`, `PDCUtil`, `GameRuleUtil`, `LoreUtil`).
- [COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md) — Commit message conventions.
- [RELEASING.md](docs/RELEASING.md) — Writing the changelog and publishing a release.

For AI-assisted development, see [CLAUDE.md](CLAUDE.md).

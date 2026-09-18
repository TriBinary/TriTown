# TriTown - Change Log

## Unreleased

### Improvements

#### Misc

+ The console now sees the same prefixed, formatted plugin messages as players instead of plain unprefixed text.

### Fixes

#### Misc

+ Fixed players seeing the "Unknown sub-command" message twice.

### Technical Details

#### Economy

+ Added the economy core: `Money` (balances held as whole minor units so repeated arithmetic cannot drift), `Currency`
  and `CurrencyRegistry`, `AccountType`, `MoneyAccount`, `EconomyResult`, `LedgerLimits`, the thread-safe
  `EconomyLedger`, and `EconomyFormat` for plain and MiniMessage rendering.

#### Misc

+ Added `PluginConfig.getLong` and `PluginConfig.getKeys` for long values and for iterating named config sections.
+ Commands can now declare `extraPermissions`, which the permission registrar registers alongside the command's own
  node so per-action permissions are visible to permission-management plugins.
+ Set up the TriTown project from the Paper plugin template.
    + Renamed the package to `net.trilleo.mc.plugins.tritown`, the main command to `/tritown` (alias `/tt`), and
      permissions to `tritown.*`.
    + Added Towny and Vault as required dependencies. `copyPlugin` copies the Towny version from `gradle.properties`
      into the test server, and `startServer` passes `--nogui`, forwards console input, and explains when `run/` has no
      Paper jar.
    + Added `EconomyUtil` for Vault economy access. TriTown disables itself when no economy plugin is installed.
    + Added `Main.instance` and `Main.reload()`, which `/tritown reload` now uses.
    + Aligned the Adventure test dependencies with Paper 26.2 (5.2.0).
    + Added build and release workflows, agent instructions, and the changelog and release guide.

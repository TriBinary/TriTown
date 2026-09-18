# TriTown - Change Log

## Unreleased

### New Features

#### Economy

+ TriTown now provides the server's economy itself. Vault, Towny and TriTown are a complete stack — no separate economy
  plugin such as EssentialsX is needed for player wallets or for town and nation banks.
    + The currency name, symbol, decimal places and formatting are all configurable, as are the starting balance, the
      balance cap and the smallest allowed payment.
    + Balances are stored as whole units of the smallest denomination, so they never drift the way decimal money in
      other plugins can, and they are written to disk atomically with a backup copy kept alongside.
    + Already running another economy plugin? Set `economy.provider.mode` to `external` and TriTown will use it
      instead. On `auto`, TriTown stands aside automatically when a known economy plugin is installed.
    + TriTown warns in the console when a second economy plugin registers after it, because Towny will not notice the
      newcomer and the two would disagree about balances.

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
+ Added the `EconomyStorage` interface and its JSON implementation, so an SQL backend can be added later without
  touching anything above it. Accounts are written atomically with a `.bak` fallback; unreadable data, a schema written
  by a newer build, and a changed currency scale each stop the plugin instead of silently losing balances.
+ Added `EconomySettings`, an immutable snapshot of the `economy` block of `config.yml`, and the `economy` section
  itself.
+ Added `EconomyService`, `AccountResolver`, `TownyAccountNaming`, `TriTownVaultEconomy` and `VaultRegistration`.
  `Main.onLoad` now loads the config and registers the Vault service, which is the only point early enough for Towny
  to find it — Towny picks its economy while enabling, and TriTown depends on Towny.
+ `EconomyUtil` gained `isInternal`, `transfer` and `formatRich`, and the startup check now reports which provider won
  instead of assuming another plugin supplies one.
+ Added Gson to the test dependencies, since it reaches the plugin through the `compileOnly` Paper API.

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

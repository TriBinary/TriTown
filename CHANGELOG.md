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
+ Added `/balance [player]`, `/pay <player> <amount>` and `/baltop [page]`.
    + `/balance` shows your own balance; checking someone else's needs `tritown.economy.balance.others`.
    + `/pay` moves money in a single step, so a payment can never go missing halfway. The smallest allowed payment is
      configurable.
    + `/baltop` lists players only by default. Town and nation banks can be included with
      `economy.commands.baltop-include-towns`, and the footer says how recently the list was rebuilt.
    + The three are registered as top-level commands as well as `/tt` sub-commands. Set
      `economy.commands.top-level-aliases` to `false` to keep them under `/tt` only. A name another plugin already owns
      stays that plugin's, and TriTown's is still reachable as `/tritown:balance`.
+ Every movement of money is now recorded, with a timestamp, both parties, the amount, the resulting balance and what
  caused it.
    + The log is written to `economy/transactions.log` and rolled into dated files as it grows; old files are removed
      after `economy.history.retention-days`.
    + Recent entries are also kept in memory per account, so viewing a history never reads from disk. Only accounts
      active since the last restart use any memory.
    + Turn the whole thing off with `economy.history.enabled` if you would rather not keep records.
+ Town and nation bank accounts now follow Towny. Renaming a town keeps its bank intact, and a new town that reuses an
  old name gets a fresh one instead of inheriting the old town's money. Deleting a town records the closure and, by
  default, removes the account; set `economy.towny.delete-accounts-on-delete` to `false` to keep it empty for auditing.
+ Money moved by Towny is now marked as such in the history, so a town's records read differently from another
  plugin's.
+ Added `/eco history [player]`, a paged menu of an account's recorded transactions showing the amount, the resulting
  balance, who was on the other side, and what caused it.
+ Added `/eco` for administering balances: `give`, `take` and `set` an amount, `reset` a player to the starting
  balance, `info` for an account's UUID, type, balances and dates, and `flush` to write changed accounts to disk
  immediately. Every action has its own permission, and any change is written out at once rather than waiting for the
  next save.

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
+ Added `BaltopCache`, rebuilt off the main thread by `EconomyFlushTask`, so `/baltop` never sorts every account on the
  server thread.
+ `CommandRegistrar` now logs when another plugin already owns a main command's name, rather than silently leaving it
  reachable only under the `tritown:` prefix.
+ Added Gson to the test dependencies, since it reaches the plugin through the `compileOnly` Paper API.
+ Documented the economy end to end: the core and storage layers, the Vault surface and why it implements `Economy`
  directly, the transaction log, the Towny lifecycle rules, and an owner-facing runbook covering provider modes,
  restart-only settings, the crash window and moving from another economy plugin.

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

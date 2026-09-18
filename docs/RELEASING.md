# TriTown - Changelog & Releasing

This guide covers how to maintain [CHANGELOG.md](../CHANGELOG.md) during development and how to publish a release on
GitHub. The release itself is automated by [.github/workflows/release.yml](../.github/workflows/release.yml) — when a
commit that changes `plugin_version` lands on `master`, the workflow builds the plugin, extracts the matching changelog
section, and creates the `vX.Y.Z` tag and GitHub Release with the jar attached. Every push and pull request is also
built by [.github/workflows/build.yml](../.github/workflows/build.yml).

Releases are created in the repository the commit is merged into. Development happens on a fork, and the release
workflow skips forks entirely, so merging a version-bump pull request into the organization repository publishes the
release there — not in the fork it came from.

## Changelog format

The changelog follows the [SkyHanni](https://github.com/hannibal002/SkyHanni) style:

```markdown
# TriTown - Change Log

## Unreleased

## Version 1.1.0

### New Features

#### Towns

+ Added daily town upkeep rewards.
    + Mayors receive money for every online resident.

### Improvements

#### Economy

+ Showed prices in the server currency format.

### Fixes

#### Nations

+ Fixed nation rewards skipping the capital.
```

Structure, top to bottom:

| Level  | Heading                        | Purpose                                                                          |
|--------|--------------------------------|----------------------------------------------------------------------------------|
| `##`   | `Unreleased` / `Version X.Y.Z` | One section per release; `Unreleased` collects entries during development        |
| `###`  | Category                       | `New Features`, `Improvements`, `Fixes`, `Technical Details`, `Removed Features` |
| `####` | Feature area                   | `Towns`, `Nations`, `Economy`, `Misc`, … — free-form, `Misc` is the catch-all    |
| `+`    | Entry                          | One change per bullet; indent `+` sub-bullets for details                        |

Rules of thumb:

- Add an entry under `## Unreleased` in the same commit (or PR) as the change itself, so the changelog never lags
  behind.
- Only include categories and feature areas that actually have entries — omit empty ones.
- Write entries for players and server owners, not developers: "Added daily town upkeep rewards", not
  "Refactored UpkeepTask". Developer-facing changes go under `Technical Details`.
- With outside contributors, append attribution like SkyHanni does:
  `+ Added X. - Name (https://github.com/Trilleo/TriTown/pull/123)`

## Versioning

`plugin_version` in [gradle.properties](../gradle.properties) is the single source of truth — it flows into the jar
filename and the `version` field of `plugin.yml` automatically. Follow semver: **patch** for bugfixes, **minor** for new
features, **major** for breaking changes (e.g. config or data resets).

## Release walkthrough

Example: releasing version `1.1.0`.

1. **Finalize the changelog** — in `CHANGELOG.md`, rename the `## Unreleased` heading to `## Version 1.1.0` and add a
   fresh empty `## Unreleased` above it:

   ```markdown
   ## Unreleased

   ## Version 1.1.0
   ...entries...
   ```

2. **Bump the version** — in `gradle.properties`:

   ```properties
   plugin_version=1.1.0
   ```

3. **Verify the build** (optional but recommended):

   ```
   ./gradlew build
   ```

4. **Commit and open a pull request** — commit the bump on your fork and open a pull request against the organization
   repository:

   ```
   git commit -am "Update: Plugin version 1.1.0 release"
   git push
   ```

   Do **not** create the tag yourself — the workflow creates `v1.1.0` when it publishes the release.

5. **Merge** — merging the pull request into `master` is the publish step; the release workflow fires because
   `gradle.properties` changed.

6. **Check the result** — the `release` workflow under the organization repo's *Actions* tab builds the jar and creates
   the GitHub Release. Verify the release page shows the changelog text and has `TriTown-1.1.0.jar` attached.

> **Never merge a version bump into the organization repository unless you have been explicitly asked to.** Merging it
> publishes a release.

## How the automation matches things up

- The workflow runs on pushes to `master` that touch `gradle.properties`, and can be started by hand from the *Actions*
  tab (*Run workflow*). It never runs its release steps in a fork.
- It reads `plugin_version` (e.g. `1.1.0`). If the tag `v1.1.0` already exists, it does nothing — changing other
  properties such as `towny_version` or `vault_api_version` never re-releases.
- Otherwise it extracts everything between `## Version 1.1.0` and the next `## ` heading in `CHANGELOG.md`. That text
  becomes the release body.
- **If no matching section exists, the workflow fails** — a release cannot ship with an empty changelog. Fix the heading
  (exact match: `## Version 1.1.0`) and re-run the workflow.
- The plugin jar from `build/libs/` is attached, and the `v1.1.0` tag is created on the merged commit.

## Fixing a botched release

- **Wrong changelog / missing section**: if the workflow failed, fix `CHANGELOG.md` through another pull request, then
  start the `release` workflow by hand (*Actions* → *release* → *Run workflow*). If a release was already published with
  the wrong text, edit the release notes on GitHub.

- **Wrong version in the jar / release on the wrong commit**: delete the release and its tag on the organization
  repository (the *Releases* page offers both), fix things through a pull request, then run the workflow by hand.

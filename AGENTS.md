# ClickControl repository instructions

Preserve unrelated work and never commit signing credentials or keystores.

## Android release policy

Use the `github-release-publishing` skill for release workflow and publishing work.
Default branch pushes update one signed Android prerelease at `development`.
The desktop workflow builds downloadable Actions artifacts and does not publish
new releases. Keep the latest historical desktop release available.

Only create/push a stable `vX.Y.Z` tag after the user explicitly asks for a stable
release. Ordinary work and requests to push do not authorize a stable release.
Follow [docs/releases/README.md](docs/releases/README.md), bump version defaults,
add release notes, run checks, inspect the intended source and wait for Actions.
Never move stable tags or overwrite published stable binaries. Release signing
requires the dedicated release key; debug-key fallback is prohibited.

Run `python3 -m unittest discover -s scripts/tests -v`,
`./gradlew :app:testDebugUnitTest :app:lintDebug` for release workflow changes.
The selected project license is GPL-3.0-or-later; preserve dependency notices.

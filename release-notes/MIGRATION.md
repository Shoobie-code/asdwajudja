# Release notes: migration template

Each `release-notes/<tag>.md` is copied into the GitHub release. Any release that changes a setting, a command, or the config file needs a **Migration** section. Copy this into the new notes file and fill it in.

```markdown
## Migration

- **Settings renamed or moved:** old name → new name, and where it is in the menu now.
- **Settings removed:** what replaces them, or "no replacement".
- **Config file:** does the new version read the old `config/skyblockminer.json`? Anything it resets or clamps (see `MinerConfig.sanitize()`).
- **Commands:** renamed or removed `/sm` commands.
- **Profiles:** whether saved profiles still load.
- **Action needed:** the steps a user must take, or "none".
```

If the release has nothing to migrate, write "Migration: none. Existing settings and profiles load as before."

# Architecture (proposal)

## Scope
Project Zomboid Build 42.21, singleplayer first. Steam launch, no dedicated launcher. ZombieBuddy integration must be validated on the target build.

## Components
- Lua UI: main-menu Cloud Saves entry, local saves list, per-save preferences, backup history, restore confirmation.
- Java bridge via ZombieBuddy: filesystem operations, async jobs, hooks around safe save lifecycle.
- Backup engine: atomic snapshot creation, manifests, SHA-256 verification, retention, restore rollback.
- Cloud provider adapter (future): Google Drive OAuth, upload/download, version listing, conflict detection.

## Save lifecycle
1. Before loading: identify save; check if cloud version differs (once cloud is implemented); create local snapshot if configured; verify completion before world load.
2. While running: no file-level snapshot of active world.
3. After exit to menu: wait for confirmed world shutdown and file writes to complete; snapshot if configured.
4. Manual mode: allow snapshots from main/load menu only when save is inactive.
5. Restore: confirm, back up current state, verify selected snapshot, restore to staging, swap only when inactive, retain rollback copy.

## Settings per save
- enabled: boolean
- mode: manual | automatic
- trigger: before_enter | after_exit | both (automatic only)
- destination: local | drive | both
- retention: 3 | 5 | 10

Cloud-only still requires a temporary local staging snapshot. Offline uploads remain queued; never report success prematurely.

## Data boundaries
Keep backups outside the game's Saves folder to avoid recursive copying. Include manifest: save identifier, game build, UTC timestamp, source device ID, snapshot hash, files, size, mod compatibility hints. Never commit saves or OAuth tokens.

## Open technical questions
- Exact ZombieBuddy API and compatible Java loading paths in Build 42.21.
- Reliable pre-load interception before the world modifies save files.
- Reliable post-save completion event when returning to menu.
- How to handle full application exit without an external service.
- Google OAuth desktop authorization and scope/appDataFolder tradeoffs.
- Build 42 save directory variants and host-mode save boundaries.

No implementation should claim these are solved before in-game testing.

# PZ Cloud Management

Save backups, restore points and planned Google Drive cloud synchronization for Project Zomboid Build 42.21.

**Status: design and early prototype; not ready for gameplay saves.** No tested backup/restore engine or Google Drive integration has been implemented.

## Planned features
- Select which worlds are protected.
- Manual or automatic backups, at entry, exit, or both.
- Local, Google Drive, or both as storage destinations.
- Restore earlier sessions without silently overwriting current progress.
- Main-menu Cloud Saves browser to download saves on another PC.
- Normal Steam launch; proposed Java integration through ZombieBuddy.

## Development
- `main`: protected baseline.
- `development`: architecture, prototypes, and integration work.

See [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), and [test plan](docs/TEST_PLAN.md).

**Safety:** test only on disposable saves until lifecycle interception, integrity verification, and rollback are validated on Build 42.21.

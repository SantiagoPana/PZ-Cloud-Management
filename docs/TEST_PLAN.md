# Test plan

Use disposable saves only until restore and lifecycle hooks are verified.

1. Start with an inactive save; create a manual snapshot; compare hashes and file counts.
2. Enter and exit the world; verify automatic triggers happen at the selected moments only.
3. Simulate interruption during compression; original save must remain intact.
4. Simulate insufficient disk space and permissions errors; fail safely.
5. Restore from a valid snapshot; verify previous current state remains recoverable.
6. Corrupt an archive; restore must refuse it.
7. Test identical save names in different modes; unique IDs must prevent collisions.
8. Test multiple large world chunks and long paths.
9. Test cloud offline/partial upload; never mark as synchronized.
10. Test on second PC with missing mods or different game build; warn before loading.

**Release gate:** do not publish restore or auto-backup as safe until real Build 42.21 testing passes.

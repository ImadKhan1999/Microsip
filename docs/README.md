# Project documentation (start here)

This folder is the shared memory for everyone (human or AI) working on the app.
Read these in order before changing anything:

| File | What it holds |
|------|---------------|
| [HANDOFF.md](HANDOFF.md) | Current state, what works, what is next. **Update at the end of every work session.** |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the app is put together, and why (decision records). |
| [FEATURES.md](FEATURES.md) | MicroSIP feature parity matrix with status per feature. |
| [BUILD.md](BUILD.md) | How to build the APK locally and in CI, signing, versions. |
| [TESTING.md](TESTING.md) | How to test, test plan, and recorded results. |
| [PROGRESS_LOG.md](PROGRESS_LOG.md) | Append-only, dated log of every step, command, result and problem. |

## Rules for contributors (including AI agents)

1. Append to `PROGRESS_LOG.md` for every meaningful step: what you did, why, the command, the result (pass/fail, error text).
2. Update `FEATURES.md` status when a feature changes state.
3. Update `HANDOFF.md` before you stop, so the next agent can continue without asking.
4. Record decisions (library choice, permission model, etc.) in `ARCHITECTURE.md` under "Decision records".
5. Never commit secrets. The committed `app/debug-shared.keystore` is intentionally public (see BUILD.md).

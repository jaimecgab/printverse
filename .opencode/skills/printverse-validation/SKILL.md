---
name: printverse-validation
description: Use after a PrintVerse change to select cumulative backend, frontend, Docker, and repository gates and report their real results.
---

# PrintVerse validation

Select gates cumulatively from the files and behavior changed. Start with the closest focused test, then run the applicable complete gates. Execute only commands permitted to the active agent; identify external gates for the human or orchestrator.

## Gate selection

### Java or backend

1. Run the closest specific test when an existing permitted command supports it; otherwise record it as an external gate.
2. Run `mvn test`.
3. Run `mvn verify` when integration, persistence, security, concurrency, or release confidence requires it.
4. Request external/orchestrator `git diff --check`.

### Frontend

Run from `frontend/`:

1. Run the closest specific test when an existing permitted command supports it; otherwise record it as an external gate.
2. Run `npm run test`.
3. Run `npm run lint`.
4. Run `npm run build`.
5. Request external/orchestrator `git diff --check`.

### Docker or Compose

1. Run `docker compose config`.
2. Treat builds, startup, smoke tests, and destructive lifecycle commands as external gates unless separately approved and permitted.

### Mixed changes

Combine every applicable section. API/DTO changes normally require both backend and frontend gates; Flyway and concurrency changes require integration evidence with Docker available.

## Failure handling

- Never report an omitted, skipped, timed-out, or blocked gate as successful.
- Do not install or update dependencies to make validation pass.
- Distinguish a regression introduced by the change from a preexisting failure or infrastructure problem.
- Preserve logs or concise evidence without exposing secrets.
- Stop and escalate when the same gate fails again after one scoped correction.
- Do not broaden the change merely to obtain a green build.

## Output

Report a compact table or list with:

- gate or command;
- executed, omitted, blocked, or external status;
- result and relevant evidence;
- reason for omission or failure;
- residual risk and required follow-up.

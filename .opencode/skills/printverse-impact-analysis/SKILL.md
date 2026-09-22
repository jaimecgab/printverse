---
name: printverse-impact-analysis
description: Use when planning a PrintVerse change to map affected backend, frontend, API, persistence, tests, and gates before implementation.
---

# PrintVerse impact analysis

Use this skill before implementation. It analyzes impact; it does not modify files or decide unresolved product questions.

## Workflow

1. Restate the requested behavior, constraints, and explicit exclusions.
2. Locate the current implementation before naming affected files.
3. Trace the relevant path across:
   - entities and state enums;
   - DTOs and validation;
   - services, transactions, locks, and repositories;
   - controllers and API contracts;
   - frontend types, API client, pages, and components;
   - migrations, tests, Docker, and configuration when relevant.
4. Check whether the change affects historical snapshots or derives current catalog data where historical data is required.
5. Identify authorization, concurrency, persistence, compatibility, and data-integrity risks.
6. Determine whether a schema change may be needed. Mark it as requiring human approval; do not assume authorization to create a migration.
7. Select the cumulative gates from `AGENTS.md` and note tests closest to the behavior.
8. Classify every impact as **confirmed**, **inferred**, or **not verified**.

## Output

Return a compact impact map containing:

- confirmed behavior and evidence;
- likely affected layers and files;
- API, snapshot, authorization, concurrency, and persistence effects;
- possible migration impact;
- tests and gates;
- open questions and stop conditions.

Do not expand the approved scope or implement changes.

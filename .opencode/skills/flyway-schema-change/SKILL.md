---
name: flyway-schema-change
description: Use when a PrintVerse change may alter PostgreSQL schema, persisted data, JPA mappings, constraints, indexes, or Flyway migrations.
---

# Flyway schema change

This skill guides migration analysis and implementation safety. Loading it does **not** authorize a migration; obtain explicit human approval before editing schema or data.

## Required process

1. Inspect all existing migrations and the current JPA mapping.
2. Confirm the schema change and data behavior are inside the approved plan.
3. Never edit, rename, reorder, or replace an applied Flyway migration.
4. Create a new versioned migration using the next valid repository version.
5. Evaluate existing rows before choosing nullability, defaults, constraints, types, or uniqueness rules.
6. Consider indexes from actual query and constraint needs; avoid speculative indexes.
7. Separate schema evolution from large or risky data transformation when that improves safety or observability.
8. Keep Hibernate `ddl-auto=validate`; application startup must validate rather than mutate schema.
9. Preserve compatibility with both a fresh database and an existing migrated database.
10. Define rollback or mitigation where practical. If rollback is unsafe, state that explicitly before implementation.

## Review checklist

- [ ] Human approval explicitly covers Flyway and data impact.
- [ ] Existing data can satisfy new constraints and nullability.
- [ ] Defaults have correct future and backfill semantics.
- [ ] Foreign keys, unique constraints, checks, and indexes have stable names and justified behavior.
- [ ] JPA mappings and DTO behavior agree with the migrated schema.
- [ ] Destructive operations, table rewrites, and locking risks are identified.
- [ ] Fresh-database and baseline/existing-database tests are selected.
- [ ] Deployment ordering, rollback, and mitigation are documented when relevant.

Stop and escalate if approval, data assumptions, compatibility, or recovery strategy is unclear.

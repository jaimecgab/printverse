---
name: printverse-domain-change
description: Use when changing PrintVerse quotation, pricing, production, material, printer, customer, or state-transition behavior.
---

# PrintVerse domain change

Use this skill for functional domain changes. Follow the approved plan and consult `AGENTS.md` for global invariants and gates.

## Domain guardrails

- Keep the backend authoritative for calculations, permissions, and state transitions.
- Preserve existing `BigDecimal` scales and `HALF_UP` rounding rules.
- Keep quotes editable only while `DRAFT` when that rule applies.
- Preserve historical customer, material, printer, and cost snapshots.
- Keep accepted-quote conversion to production idempotent.
- Preserve exclusive printer occupancy and the distinction between queued and `IN_PROGRESS` work.
- Do not weaken locks, lock ordering, `@Version`, or database constraints.
- Keep DTOs at controller boundaries; do not expose JPA entities.
- Enforce authorization in the backend even when the UI also restricts an action.
- Do not alter related rules unless the approved plan explicitly includes them.

## Before editing

- [ ] Confirm the requested state, calculation, or workflow change.
- [ ] Trace controller → DTO → service → repository/entity and the frontend consumer.
- [ ] Identify snapshots and historical records touched by the behavior.
- [ ] Identify transaction, authorization, concurrency, and schema implications.
- [ ] Locate existing tests for the rule and its failure paths.
- [ ] Stop if the change needs unapproved API, Flyway, security, or concurrency scope.

## After editing

- [ ] Confirm calculations and transitions still have one backend authority.
- [ ] Confirm historical records are not recomputed from current catalogs.
- [ ] Confirm idempotency, locks, versions, and constraints remain consistent.
- [ ] Confirm DTO and frontend contracts remain compatible or are updated within scope.
- [ ] Add or update focused success, rejection, and regression tests.
- [ ] Run the applicable gates through `printverse-validation`.
- [ ] Report deviations and residual risks instead of fixing adjacent issues.

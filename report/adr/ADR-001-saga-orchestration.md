# ADR 001 — Central Saga Orchestrator

## Status

Accepted.

## Decision

Use a dedicated synchronous REST saga orchestrator at port `8096`. It owns the
hold, confirm, and compensation sequence. Individual bridges and microservices
remain operator-local and do not coordinate other operators.

## Consequence

Failure handling is centralized and auditable, while each operator keeps its
local transaction rules and idempotency behavior.

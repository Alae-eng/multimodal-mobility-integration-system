# Section C — API Gateway and Saga Orchestrator

## Implemented

- Saga orchestrator on port `8096`
- API gateway on port `8080`
- Search across rail, air, and carsharing operators
- Hold, confirm, cancel, and reverse compensation workflow
- Idempotency keys for hold and confirm operations
- `traceparent` forwarding to downstream services
- Public gateway routes for search, booking, pricing, bookings, and notifications
- Shared RabbitMQ, Jaeger, and OpenTelemetry infrastructure
- Unit and controller tests for saga and gateway behavior

## Saga behavior

1. Hold every selected offer.
2. If any hold fails, cancel already acquired holds.
3. Confirm every hold.
4. If any confirm fails, cancel confirmed booking references and remaining holds.
5. Return the confirmed booking references or a structured conflict response.

## Important result

The implementation uses only real operators and bridges. No fake rail, air, or
carsharing service is required by the orchestration or gateway code.

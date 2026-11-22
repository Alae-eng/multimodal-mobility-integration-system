# Saga Orchestrator

The Member C orchestration service runs on port `8096`. It coordinates real rail,
air, and carsharing operator bridges:

- Rail: `http://rail-bridge:8090`
- Air: `http://air-bridge:8091`
- Carsharing: `http://carsharing:8080`

## API

Search:

```bash
curl "http://localhost:8096/api/v1/search?origin=Casablanca&destination=Rabat&date=2026-11-15"
```

Book:

```bash
curl -X POST http://localhost:8096/api/v1/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-booking-1" \
  -d '{"offerIds":["RAIL-CAS-RAB-0730","AIR-CMN-MAD-1800","CAR-RAB-001"],"seats":1}'
```

Health:

```bash
curl http://localhost:8096/api/v1/health
curl http://localhost:8096/actuator/health
```

The orchestrator holds every offer first, confirms every hold second, and
compensates confirmed bookings and unconfirmed holds in reverse order on
failure. Idempotency keys are forwarded to hold and confirm calls and successful
responses are replayed for a repeated saga request.

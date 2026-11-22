# API Gateway

The public front door runs on port `8080`.

## Routes

| Public route | Upstream |
|---|---|
| `GET /api/v1/search` | Saga search across rail, air, and carsharing |
| `POST /api/v1/bookings` | Saga booking workflow |
| `POST /api/v1/quotes` | Pricing service |
| `GET /api/v1/bookings/{bookingId}` | Bookings service |
| `GET /api/v1/notifications?travelerId=...` | Notifications service |
| `GET /api/v1/health` | Gateway health |

The gateway forwards `traceparent` and `Idempotency-Key` headers. It does not
implement hold, confirm, cancel, or operator-specific business logic.

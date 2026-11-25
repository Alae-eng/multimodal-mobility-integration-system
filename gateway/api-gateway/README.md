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
| `POST /auth` | Authentification et émission d’un JWT |

All routes except `POST /auth` and `GET /api/v1/health` require
`Authorization: Bearer <token>`. The gateway authenticates the account
configured by `GATEWAY_AUTH_USERNAME` and `GATEWAY_AUTH_PASSWORD`; it signs
short-lived JWTs with `GATEWAY_AUTH_JWT_SECRET`, which must contain at least
32 characters. 

Authenticate with `POST /auth` and a JSON body containing `username` and
`password`. The response contains `accessToken`, `tokenType` (`Bearer`) and
`expiresIn` (seconds). Send that token in the `Authorization` header on
subsequent API requests.

The gateway forwards `traceparent` and `Idempotency-Key` headers. It does not
implement hold, confirm, cancel, or operator-specific business logic.

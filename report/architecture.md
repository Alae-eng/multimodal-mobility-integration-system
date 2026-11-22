# Architecture

The platform exposes a public API gateway at port `8080`. It routes search and
booking requests to the saga orchestrator, and routes pricing, bookings, and
notifications requests to their respective services.

The saga orchestrator uses the canonical REST operator contract exposed by the
real rail and air bridges and by the carsharing service. It performs holds in
one phase, confirmations in a second phase, and compensation in reverse order
when a step fails.

Infrastructure is shared through Docker Compose: RabbitMQ carries platform
events, Jaeger displays traces, and the OpenTelemetry Collector exports traces
to Jaeger.

# Apex NAS Deployment Review

## Specification Review

- Production Compose reuses the existing NAS MySQL and does not declare a
  database service.
- Only the Nginx frontend publishes a host port; backend traffic stays on the
  Compose network.
- Backend runtime includes Java 17, Python 3, market scripts, and Python
  dependencies.
- Frontend production requests and exports use the same-origin `/apex` path.
- Secrets are represented by required environment variables and the real
  `.env.production` is ignored.
- Output files and backend logs use persistent Docker volumes.

Result: compliant with the approved design.

## Code Quality Review

One integration issue was found and fixed: the generic static-asset regex in
Nginx could take precedence over `/apex` for Swagger CSS/JS. The proxy location
now uses `^~`, so all backend paths remain routed to Spring Boot.

No remaining critical or major findings were identified. The main residual
risk is environmental: the local Docker daemon is stopped, so image builds and
live container health could not be executed on this workstation.

## Verification Evidence

- Frontend Node 20 tests: 26 passed.
- Frontend Vite production build: passed; existing large-chunk warning remains.
- Backend Maven tests: 107 passed.
- Backend Maven package: passed; executable JAR generated.
- `docker compose ... config`: passed.
- `git diff --check`: passed.
- Docker image build: not run because the Docker daemon is unavailable.

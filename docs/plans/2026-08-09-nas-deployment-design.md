# Apex NAS Deployment Design

## Goal

Deploy Apex on the Docker-enabled NAS at `100.71.129.75` while reusing the
existing MySQL instance. The application is exposed through one web port; the
backend and database remain private to the NAS or Docker network.

## Architecture

- `frontend`: build Vue with Node 20, serve the static files with Nginx, and
  proxy `/apex` to the backend service.
- `backend`: build Spring Boot with Maven and Java 17; the runtime also contains
  Python 3, the market-data scripts, and their dependencies.
- `mysql`: existing NAS container, reached by container DNS over its external
  Docker network. It is deliberately not managed by the production Compose
  file.

Only the frontend publishes a host port. Browser API and export requests use
the same-origin `/apex` prefix, avoiding CORS and client-side localhost URLs.

## Configuration And Persistence

Production secrets are supplied from an ignored `.env.production` copied from
the committed example. Spring datasource, local-login, JWT, AI, Python script,
and output-directory settings are injected as environment variables.

The backend mounts persistent directories for `.mx_output` and logs. Database
data stays in the existing MySQL deployment.

## Operational Constraints

- Do not use NAS management port `5000`; default application port is `8088`.
- Do not publish backend `8080` or MySQL `3306` unless diagnostics require it.
- Join the existing MySQL external Docker network and use its container DNS
  name; both values remain configurable for different NAS installations.
- Use a dedicated database user and replace default login/JWT credentials.

## Verification

- Frontend URL helper unit tests and production build.
- Backend Maven tests/package.
- Docker image builds and `docker compose config` validation.
- Container health checks for backend and frontend.

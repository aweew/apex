# NAS One-Command Deployment Design

## Goal

Provide one repository command that deploys Apex on Synology, reports actionable
configuration errors, waits for the backend health check, and verifies the host
port used by DSM reverse proxy.

## Behavior

- Resolve the repository from either a root-level script or the committed
  `scripts/` location, so it works from any current directory.
- Require an existing `.env.production`; never create, copy, or overwrite
  production configuration.
- Validate Docker, Compose, required secrets, the existing MySQL Docker network,
  and rendered Compose configuration before changing containers.
- Build and start the production services without stopping or deleting volumes.
- Deploy all services by default, or only backend/frontend with `--be`/`--fe`
  and Compose dependency startup disabled.
- Wait for backend health and verify the public host port through Nginx.
- Print backend/frontend logs automatically when startup or HTTP verification
  fails.
- Pull with fast-forward-only Git updates before deployment by default, retain
  `--update` as a compatibility alias, and support `--check` for prerequisite
  validation without changing files or containers.

## Safety

The script never runs `docker compose down`, never deletes volumes, never
creates or edits the production environment, and never prints secret values.

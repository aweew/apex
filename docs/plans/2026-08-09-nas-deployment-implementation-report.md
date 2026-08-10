# Apex NAS Deployment Implementation Report

## Delivered

- Multi-stage Java 17/Python backend image.
- Node 20/Nginx frontend image with `/apex` reverse proxy.
- Production Compose that reuses the NAS MySQL and exposes port `8088` only.
- Direct container networking to the existing MySQL network, avoiding NAS
  Tailscale hairpin routing.
- Secret-free production environment template and ignored local environment.
- Persistent output and log volumes plus backend health-gated frontend startup.
- Shared frontend API URL builder and removal of client-side localhost export
  links.
- Chinese NAS deployment, security, verification, upgrade, and troubleshooting
  guide.

## Deployment Entry Point

Follow `docs/NAS_DEPLOYMENT.md`, create `.env.production`, then run:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

The default application URL is `http://100.71.129.75:8088/`.

## Remaining Environment Check

Run the command above on the NAS to perform the first real image build and
confirm that its CPU architecture, package mirrors, MySQL listener, and firewall
allow the backend to become healthy.

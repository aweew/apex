# Apex NAS Deployment Implementation Plan

1. Add a frontend URL helper with tests, then replace every hard-coded backend
   export URL with a URL derived from `VITE_API_BASE`.
2. Add a Java 17/Python 3 backend image that builds the Spring Boot JAR and
   installs `scripts/market_data/requirements.txt`.
3. Add a Node 20/Nginx frontend image and same-origin `/apex` reverse proxy.
4. Add `docker-compose.nas.yml` and `.env.production.example` without managing
   or exposing MySQL.
5. Document Synology/NAS installation, database networking, startup, upgrade,
   verification, backup boundaries, and rollback.
6. Run frontend tests/build, backend tests/package, Compose rendering, image
   builds where Docker is available, and review the resulting diff.

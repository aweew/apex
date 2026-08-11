# NAS One-Command Deployment Plan

1. Add shell tests for help, missing-environment rejection without file
   creation, placeholder-secret rejection, and successful prerequisite checking
   with a mocked Docker CLI.
2. Implement a POSIX shell deployment entry point under `scripts/`.
3. Add health polling and automatic diagnostic logs.
4. Add full, backend-only, and frontend-only deployment modes.
5. Add a guarded global command installer.
6. Document first deployment, update deployment, and check-only commands.
7. Run shell tests, syntax validation, Compose rendering, and diff review.

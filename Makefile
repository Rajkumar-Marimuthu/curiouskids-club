.PHONY: dev verify format-check format-check-api format-check-web api-test web-test \
	api-build web-build e2e format api-client seed clean

COMPOSE := docker compose -f infra/local/docker-compose.yml
GRADLE := ./apps/api/gradlew -p apps/api
NPM := npm --prefix apps/web

dev:
	$(COMPOSE) up -d
	@trap 'kill 0' EXIT; \
	$(GRADLE) bootRun --args='--spring.profiles.active=local' & \
	$(NPM) run dev & \
	wait

# Full local/CI gate. CI runs the same targets split by path (see .github/workflows/ci.yml)
# so a PR touching only one app doesn't need to check out the other's toolchain.
verify: format-check api-test web-test api-build web-build

format-check: format-check-api format-check-web

format-check-api:
	$(GRADLE) spotlessCheck

format-check-web:
	$(NPM) run format:check

# Tests plus the JaCoCo coverage gates (NFR-11).
api-test:
	$(GRADLE) test jacocoTestCoverageVerification

web-test:
	$(NPM) run lint
	$(NPM) run typecheck
	$(NPM) run test

api-build:
	$(GRADLE) build -x test

web-build:
	$(NPM) run build

# Playwright against the full local stack: run `make dev` first (it serves the API on :8080).
# The web dev server is started if it is not already running.
e2e:
	$(NPM) run e2e

format:
	$(GRADLE) spotlessApply
	$(NPM) run format

# Regenerate TypeScript types for the web app from the contract. Output is committed; never edit it.
# Run via npx with a pinned version: openapi-typescript 7 declares a TypeScript 5 peer, which the
# web app's TypeScript 6 would conflict with as a devDependency.
api-client:
	cd apps/web && npx --yes openapi-typescript@7.13.0 ../../contracts/openapi.yaml -o src/api/schema.d.ts

seed:
	@echo "make seed: not implemented yet"

clean:
	$(GRADLE) clean
	rm -rf apps/web/dist

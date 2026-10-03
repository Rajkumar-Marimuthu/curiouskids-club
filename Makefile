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

api-test:
	$(GRADLE) test

web-test:
	$(NPM) run lint
	$(NPM) run typecheck

api-build:
	$(GRADLE) build -x test

web-build:
	$(NPM) run build

e2e:
	@echo "make e2e: not implemented until T-004 (Playwright setup)"

format:
	$(GRADLE) spotlessApply
	$(NPM) run format

api-client:
	@echo "make api-client: not implemented until T-003 (contracts/openapi.yaml exists)"

seed:
	@echo "make seed: not implemented yet"

clean:
	$(GRADLE) clean
	rm -rf apps/web/dist

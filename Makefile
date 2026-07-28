# Makefile for common tasks
APP_NAME=assetiq
JAR=target/assetIQ-0.0.1-SNAPSHOT.jar
PORT?=8082

.PHONY: build run clean docker-build docker-run test

build:
	mvn -DskipTests package

run: build
	java -jar $(JAR) --server.port=$(PORT)

clean:
	mvn clean

docker-build:
	docker build -t $(APP_NAME):latest .

# Run the container exposing the port
docker-run:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) $(APP_NAME):latest

test:
	mvn test

# quick image clean
docker-clean:
	docker image rm -f $(APP_NAME):latest || true

# ── Local dev backend (Docker Postgres/Redis + spring-boot:run) ────────────
# Mirrors the manual local-dev setup: Postgres/Redis run in Docker on
# remapped ports (5433/6380) to avoid clashing with other projects on this
# machine, and the backend runs directly via Maven so code changes are easy
# to iterate on. State lives in .run/ (gitignored) so start/logs/stop don't
# need to guess at PIDs or ports.
SHELL := /bin/bash
RUN_DIR := .run
PID_FILE := $(RUN_DIR)/backend.pid
LOG_FILE := $(RUN_DIR)/backend.log
LOCAL_DB_PORT := 5433
LOCAL_REDIS_PORT := 6380
BACKEND_PORT := 8080

.PHONY: dev-db-up backend-start backend-logs backend-stop backend-status

# Start the local Postgres/Redis containers (docker-compose.override.yml
# remaps them to $(LOCAL_DB_PORT)/$(LOCAL_REDIS_PORT); see that file).
dev-db-up:
	docker compose up -d postgres redis

# Start the backend in the background against local Docker Postgres/Redis.
# .env is loaded line-by-line rather than `source`d because the Neon
# connection string's unquoted "&" breaks naive sourcing and silently drops
# every variable after it (including SPRING_PROFILES_ACTIVE).
#
# `./mvnw spring-boot:run` forks a child JVM that's the actual process bound
# to $(BACKEND_PORT) — `$!` after `nohup ... &` only captures the *wrapper*
# PID, which is a different, short-lived process. Killing the wrapper alone
# leaves the real server running as an orphan. So PID_FILE is kept purely
# for reference/backend-status; every liveness check and backend-stop use
# whatever process is actually LISTENing on the port, which is the only
# reliable source of truth here.
# NOTE: this whole recipe is deliberately ONE backslash-continued shell
# command (not one line per step). GNU Make invokes each recipe LINE as
# its own subshell, so an `exit 0` guarding "already running" would only
# end that one line and Make would carry on to the next line anyway —
# on macOS's stock Make (3.81) there's no .ONESHELL: to fall back on
# either. Keeping it as a single shell invocation is what makes `exit`
# actually skip the rest.
backend-start: dev-db-up
	@mkdir -p $(RUN_DIR); \
	if lsof -ti:$(BACKEND_PORT) -sTCP:LISTEN >/dev/null 2>&1; then \
		echo "Backend already running on :$(BACKEND_PORT) (PID $$(lsof -ti:$(BACKEND_PORT) -sTCP:LISTEN))."; \
		exit 0; \
	fi; \
	echo "Starting backend on :$(BACKEND_PORT) (logs: $(LOG_FILE))..."; \
	( \
		while IFS='=' read -r key val; do \
			[[ -z "$$key" || "$$key" == \#* ]] && continue; \
			export "$$key=$$val"; \
		done < <(grep -v '^\s*#' .env | grep '='); \
		export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$(LOCAL_DB_PORT)/assetiq"; \
		export SPRING_DATASOURCE_USERNAME="assetiq"; \
		export SPRING_DATASOURCE_PASSWORD="assetiq"; \
		export REDIS_HOST="localhost"; \
		export REDIS_PORT="$(LOCAL_REDIS_PORT)"; \
		nohup ./mvnw -q spring-boot:run > $(LOG_FILE) 2>&1 & \
		echo $$! > $(PID_FILE) \
	); \
	echo "Waiting for backend to become healthy (this can take ~60s)..."; \
	for i in $$(seq 1 90); do \
		if curl -sf -o /dev/null http://localhost:$(BACKEND_PORT)/api/v1/health; then \
			echo "Backend is up (PID $$(lsof -ti:$(BACKEND_PORT) -sTCP:LISTEN))."; \
			exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "Backend did not become healthy in time — see $(LOG_FILE)"; exit 1

# Follow the backend's log output. Ctrl-C to stop watching (backend keeps running).
backend-logs:
	@test -f $(LOG_FILE) || { echo "No log file yet — run 'make backend-start' first."; exit 1; }
	tail -f $(LOG_FILE)

# Stop whatever is actually listening on $(BACKEND_PORT) — see the note on
# backend-start for why this doesn't just trust PID_FILE.
backend-stop:
	@PIDS=$$(lsof -ti:$(BACKEND_PORT) -sTCP:LISTEN 2>/dev/null); \
	if [ -n "$$PIDS" ]; then \
		echo "$$PIDS" | xargs kill -9; \
		echo "Backend (PID $$PIDS) stopped."; \
	else \
		echo "Nothing listening on :$(BACKEND_PORT)."; \
	fi
	@if [ -f $(PID_FILE) ] && kill -0 "$$(cat $(PID_FILE))" 2>/dev/null; then \
		kill -9 "$$(cat $(PID_FILE))" 2>/dev/null || true; \
	fi
	@rm -f $(PID_FILE)

# Quick check of whether the backend is alive and responding.
backend-status:
	@if lsof -ti:$(BACKEND_PORT) -sTCP:LISTEN >/dev/null 2>&1; then \
		echo "Process: running (PID $$(lsof -ti:$(BACKEND_PORT) -sTCP:LISTEN))"; \
	else \
		echo "Process: not running"; \
	fi
	@curl -sf -o /dev/null http://localhost:$(BACKEND_PORT)/api/v1/health \
		&& echo "Health check: OK" \
		|| echo "Health check: FAILED"


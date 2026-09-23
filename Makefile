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

# Local single-arch build for development. The tag comes from git so a local
# image is never ambiguous; `latest` is not used anywhere, because for a
# self-hosted operator a moving tag means an unattended schema migration.
LOCAL_TAG ?= $(shell git describe --tags --always --dirty 2>/dev/null | sed 's/^v//' || echo dev)

docker-build:
	docker build -t $(APP_NAME):$(LOCAL_TAG) --build-arg VERSION=$(LOCAL_TAG) .

# Run the container exposing the port. Mirrors the compose hardening so a local
# run surfaces read-only-filesystem problems here rather than in production.
docker-run:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) \
	  --read-only --tmpfs /tmp:rw,exec,mode=1777 \
	  --cap-drop ALL --security-opt no-new-privileges \
	  $(APP_NAME):$(LOCAL_TAG)

test:
	mvn test

# quick image clean
docker-clean:
	docker image rm -f $(APP_NAME):$(LOCAL_TAG) || true

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


# ── Self-hosted distribution ───────────────────────────────────────────────
# Multi-arch image build/publish for the self-hosted SKU. See
# scripts/publish-images.sh for the one-time ECR Public repository setup an
# operator must do before the first push.
#
# The version is derived from `git describe --tags --always`, never `latest`:
# a self-hosted operator who tracks a moving tag gets an unattended schema
# migration on their next container restart.
#
#   make images-build                        # all platforms, pushes nothing
#   make images-push ECR_ALIAS=yourAlias     # build + push to ECR Public
#   make images-version                      # print the tag that would be used

.PHONY: images-build images-push images-version ecr-login selfhosted-smoke

# Build every target platform without pushing. Multi-arch images cannot be
# loaded into the local image store, so this validates the build rather than
# producing something runnable; use `make docker-build` for a local image.
images-build:
	./scripts/publish-images.sh

images-push:
	@test -n "$(ECR_ALIAS)" || { echo "ECR_ALIAS is required, e.g. make images-push ECR_ALIAS=assetiq"; exit 1; }
	./scripts/publish-images.sh --push --alias $(ECR_ALIAS)

images-version:
	@git describe --tags --always 2>/dev/null | sed 's/^v//'

# ECR Public tokens are issued from us-east-1 regardless of where you deploy,
# and last 12 hours.
ecr-login:
	aws ecr-public get-login-password --region us-east-1 \
	  | docker login --username AWS --password-stdin public.ecr.aws

# Bring the self-hosted compose stack up from source and assert the backend
# reports healthy with Flyway migrated. Tears down on exit either way.
selfhosted-smoke:
	./assetiq-standalone/scripts/smoke-compose.sh

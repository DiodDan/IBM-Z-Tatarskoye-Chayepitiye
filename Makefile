.DEFAULT_GOAL := help

# Gradle 8 runs on JDK 21; do not inherit an incompatible macOS default JDK.
BACKEND_JAVA_HOME ?= $(shell if test -x /usr/libexec/java_home; then /usr/libexec/java_home -v 21 2>/dev/null; else printf '%s' "$$JAVA_HOME"; fi)

.PHONY: help setup frontend-install db-up db-down db-logs backend backend-echo frontend backend-build frontend-build compose-check check-java

help:
	@echo "make setup           Create .env if missing and install frontend dependencies"
	@echo "make db-up           Start PostgreSQL and wait until healthy"
	@echo "make backend         Run the Spring Boot backend"
	@echo "make backend-echo    Run the backend with the no-key echo model"
	@echo "make frontend        Run the Vite development server"
	@echo "make db-down         Stop PostgreSQL (preserves data)"
	@echo "make db-logs         Follow PostgreSQL logs"
	@echo "make backend-build   Build and check the backend"
	@echo "make frontend-build  Type-check and build the frontend"
	@echo "make compose-check   Validate Docker Compose configuration"

.env:
	cp .env.template .env

setup: .env frontend-install

frontend-install:
	cd frontend && npm ci

db-up: .env
	docker compose -f docker-compose.yaml up -d --wait postgres

db-down:
	docker compose -f docker-compose.yaml down

db-logs:
	docker compose -f docker-compose.yaml logs -f postgres

check-java:
	@test -x "$(BACKEND_JAVA_HOME)/bin/java" || { echo "Java 21 not found. Set BACKEND_JAVA_HOME to your JDK 21 installation." >&2; exit 1; }

backend: .env check-java
	cd backend && JAVA_HOME="$(BACKEND_JAVA_HOME)" ./gradlew bootRun

backend-echo: .env check-java
	cd backend && JAVA_HOME="$(BACKEND_JAVA_HOME)" ./gradlew bootRun --args='--spring.profiles.active=echo'

frontend:
	cd frontend && npm run dev

backend-build: check-java
	cd backend && JAVA_HOME="$(BACKEND_JAVA_HOME)" ./gradlew build

frontend-build:
	cd frontend && npm run build

compose-check: .env
	docker compose -f docker-compose.yaml config --quiet

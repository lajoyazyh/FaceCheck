#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/deploy/facecheck/docker-compose.prod.yml"
ENV_FILE="${FACECHECK_ENV_FILE:-/etc/facecheck/facecheck.env}"
HOST_PORT="${FACECHECK_BACKEND_HOST_PORT:-18080}"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Missing deploy env file: ${ENV_FILE}" >&2
    exit 1
fi

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build --remove-orphans
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
curl -fsS "http://127.0.0.1:${HOST_PORT}/api/health"

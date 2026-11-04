#!/bin/bash

set -euo pipefail

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------

APP_NAME=authorization-app
IMAGE_NAME=authorization-app
IMAGE_TAG=latest

NETWORK_NAME=backend-network

HOST_NAME=localhost
HOST_PORT=8282
CONTAINER_PORT=8080

ACCESS_TOKEN_SECONDS=3600s
REFRESH_TOKEN_DAYS=14d

ENV_FILE=".env"

# ------------------------------------------------------------
# Helper functions
# ------------------------------------------------------------
fail() {
 echo "ERROR: $1" >&2 exit 1
}

require_command() {
 command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' was not found."
}

require_variable() {
 local variable_name="$1"
 if [[ -z "${!variable_name:-}" ]]; then
   fail "Required variable '$variable_name' is not set."
 fi
}

# ------------------------------------------------------------
# Prerequisites
# ------------------------------------------------------------
echo "Checking prerequisites..."
require_command docker

docker info >/dev/null 2>&1 || fail "Docker Desktop is not running."
[[ -f "${ENV_FILE}" ]] || fail "Environment file '${ENV_FILE}' does not exist."

# ------------------------------------------------------------
# Load environment
# ------------------------------------------------------------
echo "Loading database configuration from ${ENV_FILE}"

set -a
source "${ENV_FILE}"
set +a

require_variable SPRING_PROFILES_ACTIVE
require_variable DB_HOST
require_variable DB_PORT
require_variable DB_NAME
require_variable DB_USER
require_variable DB_PASSWORD

# ------------------------------------------------------------
# Configuration summary
# ------------------------------------------------------------
echo
echo "Deployment configuration:"
echo " Application : ${APP_NAME}"
echo " Image : ${IMAGE_NAME}:${IMAGE_TAG}"
echo " Network : ${NETWORK_NAME}"
echo " Host port : ${HOST_PORT}"
echo " Container : ${CONTAINER_PORT}"
echo " Profile : ${SPRING_PROFILES_ACTIVE}"
echo " DB host : ${DB_HOST}"
echo " DB port : ${DB_PORT}"
echo " DB name : ${DB_NAME}"
echo " DB user : ${DB_USER}"
echo " DB password : ********"
echo

# ------------------------------------------------------------
# Docker network
# ------------------------------------------------------------
echo "Configuring app network"
if ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
  echo "Docker network '${NETWORK_NAME}' does not exist. Creating it..."
  docker network create "${NETWORK_NAME}"
else
  echo "Docker network '${NETWORK_NAME}' already exists."
fi

# ------------------------------------------------------------
# Build Docker image
# ------------------------------------------------------------
echo "Building Docker image: ${IMAGE_NAME}"

docker build \
  --progress=plain \
  -t "${IMAGE_NAME}" \
  -f Dockerfile.local .

# ------------------------------------------------------------
# Stop/remove previous container
# ------------------------------------------------------------
echo "Stopping old container if exists"

docker rm -f "${APP_NAME}" 2>/dev/null || true

# ------------------------------------------------------------
# Start application
# ------------------------------------------------------------
echo "Starting container: ${APP_NAME}"

docker run -d \
  --name ${APP_NAME} \
  --network ${NETWORK_NAME} \
  -p ${HOST_PORT}:${CONTAINER_PORT} \
  -e SERVER_PORT=${CONTAINER_PORT} \
  -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} \
  -e DB_HOST=${DB_HOST} \
  -e DB_PORT=${DB_PORT} \
  -e DB_NAME=${DB_NAME} \
  -e DB_USER=${DB_USER} \
  -e DB_PASSWORD=${DB_PASSWORD} \
  -e ACCESS_TOKEN_SECONDS=${ACCESS_TOKEN_SECONDS} \
  -e REFRESH_TOKEN_DAYS=${REFRESH_TOKEN_DAYS} \
  -e AUTH_ISSUER="http://${HOST_NAME}:${HOST_PORT}" \
  ${IMAGE_NAME}:${IMAGE_TAG}

# ------------------------------------------------------------
# Wait for container
# ------------------------------------------------------------
echo "Waiting for application to start..."
HEALTH_URL="http://${HOST_NAME}:${HOST_PORT}/actuator/health"
MAX_ATTEMPTS=30
SLEEP_SECONDS=2

echo "JWKS URL:"
echo "http://${HOST_NAME}:${HOST_PORT}/.well-known/jwks.json"

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
  echo "Health check attempt ${i}/${MAX_ATTEMPTS}"

  if curl --silent --fail "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
    echo
    echo "Application is UP."
    echo "Health check: ${HEALTH_URL}"
    echo
    exit 0
  fi

  sleep "${SLEEP_SECONDS}"
done

# ------------------------------------------------------------
# Startup failure
# ------------------------------------------------------------
echo
echo "ERROR: Application did not become healthy."
echo
echo "Container status:" docker ps -a --filter "name=${APP_NAME}"
echo
echo "Last container logs:" docker logs --tail 100 "${APP_NAME}"
exit 1
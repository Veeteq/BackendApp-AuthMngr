#!/bin/bash

set -e

APP_NAME="authorization-app"
IMAGE_NAME="authorization-app"
NETWORK_NAME="backend-network"
HOST_NAME="localhost"
HOST_PORT="8282"
CONTAINER_PORT="8080"
ACCESS_TOKEN_SECONDS=3600
REFRESH_TOKEN_DAYS=14

echo "Configuring app network"
if ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
  echo "Docker network '${NETWORK_NAME}' does not exist. Creating it..."
  docker network create "${NETWORK_NAME}"
else
  echo "Docker network '${NETWORK_NAME}' already exists."
fi

echo "Building Docker image: ${IMAGE_NAME}"

docker build \
  --progress=plain \
  -t "${IMAGE_NAME}" \
  -f Dockerfile.local .

echo "Stopping old container if exists"

docker rm -f "${APP_NAME}" 2>/dev/null || true

echo "Starting container: ${APP_NAME}"

docker run -d \
  --name "${APP_NAME}" \
  --network "${NETWORK_NAME}" \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  -e SERVER_PORT="${CONTAINER_PORT}" \
  -e ACCESS_TOKEN_SECONDS="${ACCESS_TOKEN_SECONDS}" \
  -e REFRESH_TOKEN_DAYS="${REFRESH_TOKEN_DAYS}" \
  -e AUTH_ISSUER="http://${HOST_NAME}:${HOST_PORT}" \
  "${IMAGE_NAME}"

echo "Container started"

echo "Health check URL:"
echo "http://${HOST_NAME}:${HOST_PORT}/actuator/health"

echo "JWKS URL:"
echo "http://${HOST_NAME}:${HOST_PORT}/.well-known/jwks.json"
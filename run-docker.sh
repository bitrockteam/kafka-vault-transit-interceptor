#!/usr/bin/env bash

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot

echo "Setting up the environment"
BASE_DIR=$(dirname "$0")

pushd "${BASE_DIR}/docker" || exit
docker-compose down || true
popd || exit

pushd "${BASE_DIR}/docker/vault/data" || exit
rm -rf $(ls)
popd || exit

pushd "${BASE_DIR}/docker/vault/unseal" || exit
rm -rf $(ls)
popd || exit

pushd "${BASE_DIR}/docker" || exit
docker-compose up -d
popd || exit

sleep 5

echo "Enabling Vault Transit"
docker exec -e VAULT_TOKEN="${VAULT_TOKEN}" docker_vault_1 vault secrets enable transit || true

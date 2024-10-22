#!/usr/bin/env bash

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot

echo "Deleting key $1"

docker exec -e VAULT_TOKEN="${VAULT_TOKEN}" docker_vault_1 vault delete "transit/keys/$1"

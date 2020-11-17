#!/usr/bin/env bash

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot
export TOPIC=mytopic

RED="\033[31m"
GREEN="\033[32m"
YELLOW="\033[33m"
RESET="\033[0m"

echo "Starting Kafka Consumer with interceptor..."
# shellcheck disable=SC2059
CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-console-consumer \
  --topic "$TOPIC" \
  --bootstrap-server localhost:9092 \
  --consumer-property interceptor.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer \
  --consumer-property interceptor.classes=it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor \
  --formatter it.bitrock.kafkavaulttransitinterceptor.HeaderMessageFormat \
  --property print.key=true \
  --property print.value=true \
  --property key.separator=" | " \
  "$@" | \
  sed -E "s@(.*)\|(.*)\|(.*)@$(printf "$RED") \1 $(printf "$RESET")|$(printf "$YELLOW") \2 $(printf "$RESET")|$(printf "$GREEN") \3 $(printf "$RESET")@"

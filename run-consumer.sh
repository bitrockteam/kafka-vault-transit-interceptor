#!/usr/bin/env bash

export TOPIC=mytopic

echo "Starting Kafka Consumer with no interceptor..."

CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-console-consumer \
  --topic "$TOPIC" \
  --bootstrap-server localhost:9092 \
  --formatter it.bitrock.kafkavaulttransitinterceptor.HeaderMessageFormat \
  --property print.key=true \
  --property print.value=true \
  --property key.separator=" | " \
  "$@"

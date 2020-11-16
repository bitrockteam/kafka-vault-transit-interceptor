#!/usr/bin/env bash

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot
export TOPIC=mytopic

echo "Starting Kafka Producer with interceptor..."

CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-console-producer \
  --topic "$TOPIC" \
  --bootstrap-server localhost:9092 \
  --producer-property interceptor.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer \
  --producer-property interceptor.classes=it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor \
  --property parse.key=true \
  --property key.separator="," \
  --property ignore.error=true

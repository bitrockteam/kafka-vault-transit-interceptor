#!/usr/bin/env bash

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot
export TOPIC=mytopic

echo "Starting Kafka Consumer with interceptor..."

CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-console-consumer \
  --topic "$TOPIC" \
  --bootstrap-server localhost:9092 \
  --consumer-property interceptor.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer \
  --consumer-property interceptor.classes=it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor \
  --formatter it.bitrock.kafkavaulttransitinterceptor.HeaderMessageFormat \
  --property print.key=true \
  --property print.value=true \
  --property key.separator=" | "
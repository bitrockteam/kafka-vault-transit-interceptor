#!/usr/bin/env bash

set -ex

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot

echo "Docker Compose Up"
docker-compose -f docker/docker-compose.yaml up -d

echo "Enable Vault Transit"
docker exec -e VAULT_TOKEN="${VAULT_TOKEN}" docker_vault_1 vault secrets enable transit || true

SIZE_IN_BYTES=128
NUM_RECORDS=50000
TEST_RUN=$((1 + RANDOM % 10))

echo "Baseline Producer Perf"
kafka-producer-perf-test --topic baseline-topic-$TEST_RUN \
                          --record-size $SIZE_IN_BYTES \
                          --num-records $NUM_RECORDS \
                          --throughput -1 \
                          --producer-props acks=1 \
                                           bootstrap.servers=localhost:9092


echo "Transit Interceptor Producer Perf"
CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-producer-perf-test   \
    --topic transit-interceptor-topic-$TEST_RUN \
    --record-size $SIZE_IN_BYTES \
    --num-records $NUM_RECORDS \
    --throughput -1 \
    --producer-props acks=1 \
                     bootstrap.servers=localhost:9092 \
                     interceptor.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer \
                     interceptor.classes=it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor


echo "Baseline Consumer Perf"
kafka-consumer-perf-test \
    --broker-list localhost:9092 \
    --messages $NUM_RECORDS \
    --topic baseline-topic-$TEST_RUN \
    --threads 1

echo "Transit Interceptor Consumer Perf"
CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-consumer-perf-test \
    --broker-list localhost:9092 \
    --consumer.config docker/kafka/perf-interceptor-consumer-config.properties \
    --messages $NUM_RECORDS \
    --topic transit-interceptor-topic-$TEST_RUN \
    --threads 1

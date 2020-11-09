#!/usr/bin/env bash

set -ex

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=myroot

echo "Docker Compose Up"
docker-compose -f docker/docker-compose.yaml up -d

echo "Enable Vault Transit"
docker exec -e VAULT_TOKEN="${VAULT_TOKEN}" docker_vault_1 vault secrets enable transit || true

SIZE_IN_BYTES=(10 100 500 1000 10000 100000)
NUM_RECORDS=50000
TEST_RUN=$((1 + RANDOM % 10))

rm results/*.txt
mkdir -p results

for size in "${SIZE_IN_BYTES[@]}"; do
    echo "Baseline Producer Perf"
    kafka-producer-perf-test --topic baseline-topic-$TEST_RUN-$size \
                            --record-size $size \
                            --num-records $NUM_RECORDS \
                            --throughput -1 \
                            --producer-props acks=1 \
                                             linger.ms=50 \
                                             compression.type=none \
                                             batch.size=16384 \
                                             bootstrap.servers=localhost:9092 > results/producer-baseline-$TEST_RUN-$NUM_RECORDS-$size.txt

    echo "Transit Interceptor Producer Perf"
    CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-producer-perf-test   \
        --topic transit-interceptor-topic-$TEST_RUN-$size \
        --record-size $size \
        --num-records $NUM_RECORDS \
        --throughput -1 \
        --producer-props acks=1 \
                         linger.ms=50 \
                         compression.type=none \
                         batch.size=16384 \
                         bootstrap.servers=localhost:9092 \
                         interceptor.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer \
                         interceptor.classes=it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor > results/producer-interceptor-$TEST_RUN-$NUM_RECORDS-$size.txt

    echo "Baseline Consumer Perf"
    kafka-consumer-perf-test \
        --broker-list localhost:9092 \
        --messages $NUM_RECORDS \
        --topic baseline-topic-$TEST_RUN-$size \
        --threads 1 > results/consumer-baseline-$TEST_RUN-$NUM_RECORDS-$size.txt

    echo "Transit Interceptor Consumer Perf"
    CLASSPATH="target/kafka-vault-transit-interceptor-1.0-SNAPSHOT-jar-with-dependencies.jar:${CLASSPATH}" kafka-consumer-perf-test \
        --broker-list localhost:9092 \
        --consumer.config docker/kafka/perf-interceptor-consumer-config.properties \
        --messages $NUM_RECORDS \
        --topic transit-interceptor-topic-$TEST_RUN-$size \
        --threads 1 > results/consumer-interceptor-$TEST_RUN-$NUM_RECORDS-$size.txt
done;

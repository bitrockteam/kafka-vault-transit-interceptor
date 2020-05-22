# Kafka Interceptor: Vault Transit

Apache Kafka [Consumer](https://kafka.apache.org/25/javadoc/org/apache/kafka/clients/consumer/ConsumerInterceptor.html)
and
[Producer](https://kafka.apache.org/25/javadoc/org/apache/kafka/clients/producer/ProducerInterceptor.html)
Interceptor to encrypt and decrypt in-transit data using HashiCorp Vault Transit secrets engine.

This interceptors could be added to Kafka Connectors via configuration and to other off-the-shelf components like Kafka REST Proxy, KSQL and so on.

## Build

```bash
maven package
```

## Test

```bash
maven test
```

## Setup

Here's some example configuration to use Vault Transit Interceptor.

### Producer Kafka Clients

Add Interceptor to Producer Configuration:

```java
properties.put("interceptor.classes", "it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor");
properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
properties.put("interceptor.value.serializer", "...");
```

`interceptor.value.serializer` must be configured according to the kind of value you want to write in Kafka, which you would have usually put in `value.serializer`.

### Consumer Kafka Clients

```java
properties.put("interceptor.classes", "it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor");
properties.put("key.deserializer", "org.apache.kafka.common.deserialization.StringDeserializer");
properties.put("value.deserializer", "org.apache.kafka.common.deserialization.StringDeserializer");
properties.put("interceptor.value.deserializer", "...");
```

`interceptor.value.deserializer` must be configured according to the kind of value you want to write in Kafka, which you would have usually put in `value.deserializer`.

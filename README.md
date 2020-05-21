# Kafka Interceptor: Vault Transit

Kafka [Consumer](https://kafka.apache.org/25/javadoc/org/apache/kafka/clients/consumer/ConsumerInterceptor.html)
and
[Producer](https://kafka.apache.org/25/javadoc/org/apache/kafka/clients/producer/ProducerInterceptor.html)
Interceptor to encrypt and decript data in transit using vault.

This interceptors could be added to Kafka Connectors via configuration and to other off-the-shelf
components like Kafka REST Proxy, KSQL and so on.

## Installation

### Producer Interceptor

Producer Interceptor allows to ...

#### Kafka Clients

Add Interceptor to Producer Configuration:

```java
    producerConfig.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, Collections.singletonList(EncryptingProducerInterceptor.class));
    //or
    producerConfig.put("interceptor.classes", "it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor");
```
### Consumer Interceptor

Consumer Interceptor allows to ...

#### Kafka Clients

```java
    consumerConfig.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, Collections.singletonList(DecryptingConsumerInterceptor.class));
    //or
    consumerConfig.put("interceptor.classes", "it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor");
```

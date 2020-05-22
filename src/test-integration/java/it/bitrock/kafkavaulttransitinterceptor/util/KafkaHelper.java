package it.bitrock.kafkavaulttransitinterceptor.util;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class KafkaHelper {


  public static KafkaProducer<String, Long> longKafkaProducerInterceptor(String bootstrapServers) {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("interceptor.value.serializer", "org.apache.kafka.common.serialization.LongSerializer");
    properties.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
      "it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor");

    return new KafkaProducer<>(properties);
  }

  public static  KafkaProducer<String, String> stringKafkaProducerInterceptor(String bootstrapServers) {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("interceptor.value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
      "it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor");

    return new KafkaProducer<>(properties);
  }

  private static Properties defaultStringConsumerProperties(String bootstrapServers) {
    final Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, String.format("%s", UUID.randomUUID().toString()));
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    return props;
  }

  public static KafkaConsumer<String, String> stringKafkaConsumer(String bootstrapServers){
    final Properties props = defaultStringConsumerProperties(bootstrapServers);

    return new KafkaConsumer<String, String>(props);
  }

  public static KafkaConsumer<String, String> stringKafkaConsumerInterceptor(String bootstrapServers){
    final Properties props = defaultStringConsumerProperties(bootstrapServers);
    props.put("interceptor.value.deserializer", StringDeserializer.class.getName());
    props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
      "it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor");

    return new KafkaConsumer<String, String>(props);
  }

  private static Properties defaultLongConsumerProperties(String bootstrapServers) {
    final Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, String.format("%s", UUID.randomUUID().toString()));
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    return props;
  }

  public static KafkaConsumer<String, Long> longKafkaConsumer(String bootstrapServers){
    final Properties props = defaultLongConsumerProperties(bootstrapServers);

    return new KafkaConsumer<String, Long>(props);
  }

  public static KafkaConsumer<String, Long> longKafkaConsumerInterceptor(String bootstrapServers){
    final Properties props = defaultLongConsumerProperties(bootstrapServers);

    props.put("interceptor.value.deserializer", LongDeserializer.class.getName());
    props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
      "it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor");

    return new KafkaConsumer<String, Long>(props);
  }

  public static <K, V> ConsumerRecords<K, V> getRecords(Consumer<K, V> consumer, long timeout, int minRecords) {
    Map<TopicPartition, List<ConsumerRecord<K, V>>> records = new HashMap<>();
    long remaining = timeout;
    int count = 0;
    do {
      long t1 = System.currentTimeMillis();
      ConsumerRecords<K, V> received = consumer.poll(Duration.ofMillis(remaining));
      System.out.println("Received: " + received.count() + ", "
        + received.partitions().stream()
        .flatMap(p -> received.records(p).stream())
        // map to same format as send metadata toString()
        .map(r -> r.topic() + "-" + r.partition() + "@" + r.offset())
        .collect(Collectors.toList()));
      if (received == null) {
        throw new IllegalStateException("null received from consumer.poll()");
      }
      if (minRecords < 0) {
        return received;
      }
      else {
        count += received.count();
        received.partitions().forEach(tp -> {
          List<ConsumerRecord<K, V>> recs = records.computeIfAbsent(tp, part -> new ArrayList<>());
          recs.addAll(received.records(tp));
        });
        remaining -= System.currentTimeMillis() - t1;
      }
    }
    while (count < minRecords && remaining > 0);
    return new ConsumerRecords<>(records);
  }
}

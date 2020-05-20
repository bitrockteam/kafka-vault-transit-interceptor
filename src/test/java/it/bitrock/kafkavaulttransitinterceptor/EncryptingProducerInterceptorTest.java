package it.bitrock.kafkavaulttransitinterceptor;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;


public class EncryptingProducerInterceptorTest {

  private final ProducerRecord<String, String> record = new ProducerRecord<>("topic", "value");




  @Test
  public void shouldNotCrash() {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    //Add ProducerInterceptor like this
    properties.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
      "it.bitrock.kafkavaulttransitinterceptor.EncryptingProducerInterceptor");
    KafkaProducer producer = new KafkaProducer<String, String>(properties);

    try {
       producer.send(record).get();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } finally {
      producer.flush();
      producer.close();
    }



    final Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
      "localhost:9092");
    props.put(ConsumerConfig.GROUP_ID_CONFIG,
      "KafkaExampleConsumer");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class.getName());
    props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
      "it.bitrock.kafkavaulttransitinterceptor.DecryptingConsumerInterceptor");

    // Create the consumer using props.
    final Consumer<String, String> consumer =
      new KafkaConsumer<>(props);

    // Subscribe to the topic.
    consumer.subscribe(Collections.singletonList("topic"));
    final int giveUp = 100;   int noRecordsCount = 0;

    while (true) {
      final ConsumerRecords<String, String> consumerRecords =
        consumer.poll(1000);

      if (consumerRecords.count()==0) {
        noRecordsCount++;
        if (noRecordsCount > giveUp) break;
        else continue;
      }

      consumerRecords.forEach(record -> {
        System.out.printf("Consumer Record:(%s, %s, %d, %d)\n",
          record.key(), record.value(),
          record.partition(), record.offset());
      });

      consumer.commitAsync();
    }
    consumer.close();
    System.out.println("DONE");
  }
}

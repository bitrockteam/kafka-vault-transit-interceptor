package it.bitrock.kafkavaulttransitinterceptor;

import it.bitrock.kafkavaulttransitinterceptor.util.KafkaHelper;
import it.bitrock.kafkavaulttransitinterceptor.util.VaultContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.testcontainers.containers.KafkaContainer;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class TransitInterceptorTest {

  @ClassRule
  public static final VaultContainer container = new VaultContainer();

  @ClassRule
  public static KafkaContainer kafka = new KafkaContainer("5.4.2");

  @ClassRule
  public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @BeforeClass
  public static void setupClass() throws IOException, InterruptedException {
    container.initAndUnsealVault();
    container.setupBackendTransit();
    environmentVariables.set("VAULT_ADDR", container.getAddress());
    environmentVariables.set("VAULT_TOKEN", container.rootToken);
  }

  @Test
  public void testStringFlow() {
    final String topic = "topic-string-key-string-value";
    final ProducerRecord<String, String> record = new ProducerRecord<>(topic, UUID.randomUUID().toString(), "plaintext-data");
    KafkaProducer<String, String> producer = KafkaHelper.stringKafkaProducerInterceptor(kafka.getBootstrapServers());

    try {
      producer.send(record).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      producer.flush();
      producer.close();
    }

    // Check that data is encrypted without interceptor
    final KafkaConsumer<String, String> consumerWithoutInterceptor = KafkaHelper.stringKafkaConsumer(kafka.getBootstrapServers());
    consumerWithoutInterceptor.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, String> recordsEncrypted = KafkaHelper.getRecords(consumerWithoutInterceptor, 3000, -1);
    assertEquals(1, recordsEncrypted.count());
    for (ConsumerRecord<String, String> consumedRecord: recordsEncrypted) {
      assertNotEquals("plaintext-data", consumedRecord.value());
    }


    // Check that data can be decrypted with the interceptor
    KafkaConsumer<String, String> consumer = KafkaHelper.stringKafkaConsumerInterceptor(kafka.getBootstrapServers());
    consumer.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, String> records = KafkaHelper.getRecords(consumer, 3000, -1);

    assertEquals(1, records.count());
    for (ConsumerRecord<String, String> consumedRecord: records) {
      assertEquals("plaintext-data", consumedRecord.value());
    }
    consumer.close();

  }

  @Test
  public void testLongFlow() {
    final String topic = "topic-string-key-long-value";
    final ProducerRecord<String, Long> record = new ProducerRecord<>(topic, UUID.randomUUID().toString(), 123456L);
    KafkaProducer<String, Long> producer = KafkaHelper.longKafkaProducerInterceptor(kafka.getBootstrapServers());

    try {
      producer.send(record).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      producer.flush();
      producer.close();
    }

    // Check that data is encrypted without interceptor
    final KafkaConsumer<String, Long> consumerWithoutInterceptor = KafkaHelper.longKafkaConsumer(kafka.getBootstrapServers());
    consumerWithoutInterceptor.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, Long> recordsEncrypted = KafkaHelper.getRecords(consumerWithoutInterceptor, 3000, -1);
    assertEquals(1, recordsEncrypted.count());
    for (ConsumerRecord<String, Long> consumedRecord: recordsEncrypted) {
      assertNotEquals(new Long(123456L), consumedRecord.value());
    }

    // Check that data can be decrypted with the interceptor
    final KafkaConsumer<String, Long> consumer = KafkaHelper.longKafkaConsumerInterceptor(kafka.getBootstrapServers());
    consumer.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, Long> records = KafkaHelper.getRecords(consumer, 3000, -1);

    assertEquals(1, records.count());
    for (ConsumerRecord<String, Long> consumedRecord: records) {
      assertEquals(new Long(123456L), consumedRecord.value());
    }
    consumer.close();

  }

}

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TransitInterceptorTest {

  @ClassRule
  public static final VaultContainer vault = new VaultContainer();

  @ClassRule
  public static final KafkaContainer kafka = new KafkaContainer("5.5.1");

  @ClassRule
  public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @BeforeClass
  public static void setupClass() throws IOException, InterruptedException {
    vault.initAndUnsealVault();
    vault.setupBackendTransit();
    environmentVariables.set("VAULT_ADDR", vault.getAddress());
    environmentVariables.set("VAULT_TOKEN", vault.rootToken);
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
    KafkaProducer<String, Long> producer = KafkaHelper.longKafkaProducerInterceptor(kafka.getBootstrapServers(), 500L);

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


  @Test
  public void testRotateKeyFlow() {
    final String topic = "topic-string-key-v2-long-value";
    final String key = UUID.randomUUID().toString();
    final ProducerRecord<String, Long> record = new ProducerRecord<>(topic, key, 123456L);
    KafkaProducer<String, Long> producer = KafkaHelper.longKafkaProducerInterceptor(kafka.getBootstrapServers(), 500L);

    try {
      producer.send(record).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      producer.flush();
    }

    try {
      vault.rotateKey(key);
      TimeUnit.MILLISECONDS.sleep(500);
    } catch (Exception ignored) {};


    final ProducerRecord<String, Long> record2 = new ProducerRecord<>(topic, key, 123456L);
    try {
      producer.send(record2).get();
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
    assertEquals(2, recordsEncrypted.count());
    for (ConsumerRecord<String, Long> consumedRecord: recordsEncrypted) {
      assertNotEquals(new Long(123456L), consumedRecord.value());
    }

    // Check that data can be decrypted with the interceptor
    final KafkaConsumer<String, Long> consumer = KafkaHelper.longKafkaConsumerInterceptor(kafka.getBootstrapServers());
    consumer.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, Long> records = KafkaHelper.getRecords(consumer, 3000, -1);

    assertEquals(2, records.count());
    final List<ConsumerRecord<String, Long>> messages = new ArrayList<>();
    for (ConsumerRecord<String, Long> consumedRecord: records) {
      assertEquals(new Long(123456L), consumedRecord.value());
      messages.add(consumedRecord);
    }

    // check different key version are used after key rotate
    assertNotEquals(KafkaHelper.getEncryptionKeyVersion(messages.get(0)),KafkaHelper.getEncryptionKeyVersion(messages.get(1)));

    consumer.close();

  }

  @Test
  public void testDeleteKeyFlow() {
    final String topic = "topic-string-key-delete-long-value";
    final String key = UUID.randomUUID().toString();
    final ProducerRecord<String, Long> record = new ProducerRecord<>(topic, key, 123456L);
    KafkaProducer<String, Long> producer = KafkaHelper.longKafkaProducerInterceptor(kafka.getBootstrapServers(), 500L);

    try {
      producer.send(record).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    } finally {
      producer.flush();
    }

    try {
      vault.deleteKey(key);
      TimeUnit.MILLISECONDS.sleep(500);

    } catch (Exception ignored) {};

    // Check that data is encrypted without interceptor
    final KafkaConsumer<String, Long> consumerWithoutInterceptor = KafkaHelper.longKafkaConsumer(kafka.getBootstrapServers());
    consumerWithoutInterceptor.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, Long> recordsEncrypted = KafkaHelper.getRecords(consumerWithoutInterceptor, 3000, -1);
    assertEquals(1, recordsEncrypted.count());
    for (ConsumerRecord<String, Long> consumedRecord: recordsEncrypted) {
      assertNotEquals(new Long(123456L), consumedRecord.value());
    }
    consumerWithoutInterceptor.close();

    // Check that data cannot be decrypted with the interceptor
    final KafkaConsumer<String, Long> consumer = KafkaHelper.longKafkaConsumerInterceptor(kafka.getBootstrapServers());
    consumer.subscribe(Collections.singletonList(topic));
    ConsumerRecords<String, Long> records = KafkaHelper.getRecords(consumer, 3000, -1);

    assertEquals(0, records.count());

  }





}

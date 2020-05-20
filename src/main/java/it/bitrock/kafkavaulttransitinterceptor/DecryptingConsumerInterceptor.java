package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.json.JsonArray;
import com.bettercloud.vault.json.JsonObject;
import com.bettercloud.vault.response.LogicalResponse;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.*;
import static java.util.stream.Collectors.groupingBy;

public class DecryptingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

  TransitConfiguration configuration;
  Vault vault;
  String mount;
  String key;

  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    if (records.isEmpty()) return records;

    Map<TopicPartition, List<ConsumerRecord<K, V>>> decryptedRecordsMap = new HashMap<>();
    for (TopicPartition partition : records.partitions()) {
      List<ConsumerRecord<K, V>> decryptedRecordsPartition =
        records.records(partition).stream()
        .collect(groupingBy(record -> new String(record.headers().headers("x-vault-encryption-key").iterator().next().value()))).values()
        .stream().flatMap(recordsPerKey -> processBulkDecrypt(recordsPerKey).stream())
        .collect(Collectors.toList());
      
      decryptedRecordsMap.put(partition, decryptedRecordsPartition);
    };

    return  new ConsumerRecords<K, V>(decryptedRecordsMap);
  }


  private List<ConsumerRecord<K, V>> processBulkDecrypt(List<ConsumerRecord<K, V>> records) {
    JsonArray batch = new JsonArray();
    String key = new String(records.get(0).headers().headers("x-vault-encryption-key").iterator().next().value());
    for (Object text : records.stream().map(ConsumerRecord::value).toArray()) {
      batch.add(new JsonObject().add("ciphertext", (String) text));
    }
    LogicalResponse response = null;
    try {
      response = vault.logical().write(String.format("%s/decrypt/%s", mount, key),
        Collections.singletonMap("batch_input", batch));
      if (response.getRestResponse().getStatus() == 200) {
        List<String> plainTexts = response.getDataObject().get("batch_results").asArray().values().stream().map(it ->
          it.asObject().get("plaintext").asString()).collect(Collectors.toList());

        AtomicInteger index = new AtomicInteger(0);
        return records.stream().map(record -> new ConsumerRecord<K, V>(record.topic(), record.partition(), record.offset(), record.timestamp(), record.timestampType(), record.checksum(), record.serializedKeySize(), record.serializedValueSize(), record.key(), (V) plainTexts.get(index.getAndIncrement()), record.headers(), record.leaderEpoch())).collect(Collectors.toList());
      } else {
        throw new RuntimeException("Decrypt failed");
      }
    } catch (VaultException e) {
      LOGGER.error("Failed to decrypt bulk records Vault", e);
      throw new RuntimeException("Failed to decrypt bulk records Vault");
    }
  }

  public void close() {
    // Do nothing
  }

  public void onCommit(Map<TopicPartition, OffsetAndMetadata> map) {
    // Do nothing
  }

  public void configure(Map<String, ?> configs) {
    configuration = new TransitConfiguration(configs);
    vault = new VaultFactory(configuration).vault;
    mount = configuration.getStringOrDefault(TRANSIT_MOUNT_CONFIG, TRANSIT_MOUNT_DEFAULT);
    key = configuration.getStringOrDefault(TRANSIT_KEY_CONFIG, TRANSIT_KEY_DEFAULT);
  }
}

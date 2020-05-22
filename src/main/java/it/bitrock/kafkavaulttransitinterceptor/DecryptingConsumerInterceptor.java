package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.json.JsonArray;
import com.bettercloud.vault.json.JsonObject;
import com.bettercloud.vault.json.JsonValue;
import com.bettercloud.vault.response.LogicalResponse;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;

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
  Deserializer<V> valueDeserializer;

  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    if (records.isEmpty()) return records;

    Map<TopicPartition, List<ConsumerRecord<K, V>>> decryptedRecordsMap = new HashMap<>();
    for (TopicPartition partition : records.partitions()) {
      List<ConsumerRecord<K, V>> decryptedRecordsPartition =
        records.records(partition).stream()
          .collect(groupingBy(record -> getEncryptionKey((ConsumerRecord<K, V>) record))).values()
          .stream().flatMap(recordsPerKey -> processBulkDecrypt(recordsPerKey).stream())
          .collect(Collectors.toList());

      decryptedRecordsMap.put(partition, decryptedRecordsPartition);
    }
    ;

    return new ConsumerRecords<K, V>(decryptedRecordsMap);
  }

  private List<ConsumerRecord<K, V>> processBulkDecrypt(List<ConsumerRecord<K, V>> records) {
    JsonArray batch = new JsonArray();
    String key = getEncryptionKey(records.get(0));
    for (Object text : records.stream().map(ConsumerRecord::value).toArray()) {
      if (text instanceof byte[]) {
        batch.add(new JsonObject().add("ciphertext", new String((byte[]) text)));
      } else {
        batch.add(new JsonObject().add("ciphertext", (String) text));
      }
    }
    LogicalResponse response = null;
    try {
      response = vault.logical().write(String.format("%s/decrypt/%s", mount, key),
        Collections.singletonMap("batch_input", batch));
      if (response.getRestResponse().getStatus() == 200) {
        List<byte[]> plainTexts = getBatchResults(response)
          .stream().map(this::getPlaintextData)
          .collect(Collectors.toList());
        AtomicInteger index = new AtomicInteger(0);
        return records.stream()
          .map(record ->
            new ConsumerRecord<K, V>(record.topic(),
              record.partition(),
              record.offset(),
              record.timestamp(),
              record.timestampType(),
              record.checksum(),
              record.serializedKeySize(),
              record.serializedValueSize(),
              record.key(),
              valueDeserializer.deserialize(record.topic(), plainTexts.get(index.getAndIncrement())),
              record.headers(),
              record.leaderEpoch()))
          .collect(Collectors.toList());
      } else {
        LOGGER.error(String.format("Decryption failed with status code: %d body: %s", response.getRestResponse().getStatus(), new String(response.getRestResponse().getBody())));
        throw new RuntimeException("Decryption failed");
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
    try {
      valueDeserializer = (Deserializer<V>) Class.forName(configuration.getString("interceptor.value.deserializer")).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      LOGGER.error("Failed to create instance of interceptor.value.deserializer", e);
    }
    vault = new VaultFactory(configuration).vault;
    mount = configuration.getStringOrDefault(TRANSIT_MOUNT_CONFIG, TRANSIT_MOUNT_DEFAULT);
    key = configuration.getStringOrDefault(TRANSIT_KEY_CONFIG, TRANSIT_KEY_DEFAULT);
  }

  private String getEncryptionKey(ConsumerRecord<K, V> record) {
    return new String(record.headers().headers("x-vault-encryption-key").iterator().next().value());
  }

  private byte[] getPlaintextData(JsonValue it) {
    return Base64.getDecoder().decode(it.asObject().get("plaintext").asString());
  }

  private List<JsonValue> getBatchResults(LogicalResponse response) {
    return response.getDataObject().get("batch_results").asArray().values();
  }
}

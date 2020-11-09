package it.bitrock.kafkavaulttransitinterceptor;

import it.bitrock.kafkavaulttransitinterceptor.util.EncryptorAesGcm;
import it.bitrock.kafkavaulttransitinterceptor.util.SelfExpiringHashMap;
import it.bitrock.kafkavaulttransitinterceptor.util.SelfExpiringMap;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.RawTransitKey;
import org.springframework.vault.support.TransitKeyType;
import org.springframework.vault.support.VaultTransitKey;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.*;
import static java.util.stream.Collectors.groupingBy;

public class DecryptingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

  TransitConfiguration configuration;
  VaultTransitOperations transit;
  Deserializer<V> valueDeserializer;
  final SelfExpiringMap<String, byte[]> map = new SelfExpiringHashMap<>();
  long lifeTimeMillis;


  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    if (records.isEmpty()) return records;

    Map<TopicPartition, List<ConsumerRecord<K, V>>> decryptedRecordsMap = new HashMap<>();
    for (TopicPartition partition : records.partitions()) {
      List<ConsumerRecord<K, V>> decryptedRecordsPartition =
        records.records(partition).stream()
          .collect(groupingBy(this::getEncryptionKeyName)).values()
          .stream().flatMap(recordsPerKey -> processBulkDecrypt(recordsPerKey).stream())
          .collect(Collectors.toList());

      decryptedRecordsMap.put(partition, decryptedRecordsPartition);
    }

    return new ConsumerRecords<>(decryptedRecordsMap);
  }

  private List<ConsumerRecord<K, V>> processBulkDecrypt(List<ConsumerRecord<K, V>> records) {
    String keyName = getEncryptionKeyName(records.get(0));
    return records.stream().map(
      record -> decryptRecord(record, keyName)
    ).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private ConsumerRecord<K, V> decryptRecord(ConsumerRecord<K, V> record, String keyName) {
    byte[] ciphertext = (byte[]) record.value();
    int encryptionVersion = getEncryptionKeyVersion(record);
    String keyCacheKey = keyName.concat("-").concat(String.valueOf(encryptionVersion));
    byte[] decodedKey = map.get(keyCacheKey);
    if (decodedKey == null) {
      VaultTransitKey vaultTransitKey = transit.getKey(keyName);
      if (vaultTransitKey != null) {
        int minDecryptionVersion = vaultTransitKey.getMinDecryptionVersion();
        if (minDecryptionVersion <= encryptionVersion) {
          RawTransitKey vaultKey = transit.exportKey(keyName, TransitKeyType.ENCRYPTION_KEY);
          // decode the base64 encoded string
          decodedKey = Base64.getDecoder().decode(vaultKey.getKeys().get(String.valueOf(encryptionVersion)));
          map.put(keyCacheKey, decodedKey, lifeTimeMillis);
        } else {
          return null;
        }
      } else {
        return null;
      }
    }

    // rebuild keyName using SecretKeySpec
    SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

    return new ConsumerRecord<>(record.topic(),
      record.partition(),
      record.offset(),
      record.timestamp(),
      record.timestampType(),
      null,
      record.serializedKeySize(),
      record.serializedValueSize(),
      record.key(),
      valueDeserializer.deserialize(record.topic(), EncryptorAesGcm.decryptWithPrefixIV(ciphertext, originalKey)),
      record.headers(),
      record.leaderEpoch());
  }

  public void close() {
    // Do nothing
  }

  public void onCommit(Map<TopicPartition, OffsetAndMetadata> map) {
    // Do nothing
  }

  public void configure(Map<String, ?> configs) {
    try {
      VaultFactory vault = new VaultFactory(configs);
      transit = vault.transit;
      configuration = vault.configuration;
      lifeTimeMillis = configuration.getLongOrDefault(TRANSIT_KEY_TTL_CONFIG, TRANSIT_KEY_TTL_DEFAULT);
    } catch (Exception ignored) {
      LOGGER.error("Failed to create Vault Client");
    }
    try {
      valueDeserializer = (Deserializer<V>) Class.forName(configuration.getString("interceptor.value.deserializer")).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      LOGGER.error("Failed to create instance of interceptor.value.deserializer", e);
    }
  }

  private String getEncryptionKeyName(ConsumerRecord<K, V> record) {
    return new String(record.headers().headers("x-vault-encryption-key-name").iterator().next().value());
  }

  private int getEncryptionKeyVersion(ConsumerRecord<K, V> record) {
    return fromByteArray(record.headers().headers("x-vault-encryption-key-version").iterator().next().value());
  }

  private int fromByteArray(byte[] bytes) {
    return ByteBuffer.wrap(bytes).getInt();
  }
}

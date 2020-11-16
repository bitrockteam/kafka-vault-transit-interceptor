package it.bitrock.kafkavaulttransitinterceptor;

import it.bitrock.kafkavaulttransitinterceptor.util.EncryptorAesGcm;
import it.bitrock.kafkavaulttransitinterceptor.util.SelfExpiringHashMap;
import it.bitrock.kafkavaulttransitinterceptor.util.SelfExpiringMap;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.RawTransitKey;
import org.springframework.vault.support.TransitKeyType;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.*;

public class EncryptingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  TransitConfiguration configuration;
  VaultTransitOperations transit;
  String defaultKey;
  Serializer<V> valueSerializer;
  final SelfExpiringMap<String, byte[]> map = new SelfExpiringHashMap<>();
  long lifeTimeMillis;

  public ProducerRecord onSend(ProducerRecord<K, V> record) {
    if (record.value() == null) return record;
    String encryptionKeyName = extractKeyOrElse(record.key(), defaultKey);
    int encryptionKeyVersion = 1;

    byte[] decodedKey = map.get(encryptionKeyName);
    if (decodedKey == null) {
      VaultTransitKey vaultTransitKey = transit.getKey(encryptionKeyName);
      if (vaultTransitKey == null) {
        transit.createKey(encryptionKeyName, VaultTransitKeyCreationRequest.builder().exportable(true).build());
        vaultTransitKey = transit.getKey(encryptionKeyName);
      }
      encryptionKeyVersion = vaultTransitKey.getLatestVersion();
      RawTransitKey vaultKey = transit.exportKey(encryptionKeyName, TransitKeyType.ENCRYPTION_KEY);
      // decode the base64 encoded string
      decodedKey = Base64.getDecoder().decode(vaultKey.getKeys().get(String.valueOf(encryptionKeyVersion)));
      map.put(encryptionKeyName, decodedKey, lifeTimeMillis);
    }

    // encrypt and decrypt need the same IV.
    // AES-GCM needs IV 96-bit (12 bytes)
    byte[] iv = EncryptorAesGcm.getRandomNonce(EncryptorAesGcm.IV_LENGTH_BYTE);

    // rebuild key using SecretKeySpec
    SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

    byte[] ciphertext = null;
    try {
      ciphertext = EncryptorAesGcm.encryptWithPrefixIV(valueSerializer.serialize(record.topic(), record.value()), originalKey, iv);
    } catch (Exception e) {
      LOGGER.error("Failed to encrypt");
    }
    Headers headers = record.headers();
    headers.add("x-vault-encryption-key-name", encryptionKeyName.getBytes());
    headers.add("x-vault-encryption-key-version", intToByteArray(encryptionKeyVersion));
    return new ProducerRecord<K, byte[]>(
      record.topic(),
      record.partition(),
      record.timestamp(),
      record.key(),
      ciphertext,
      headers
    );
  }

  private byte[] intToByteArray(final int i) {
    return ByteBuffer.allocate(4).putInt(i).array();
  }

  private String extractKeyOrElse(K key, String defaultKey) {
    if (key instanceof String) {
      return (String) key;
    } else if (key instanceof byte[]) {
      return (new String((byte[])key, StandardCharsets.UTF_8)).trim();
    } else return defaultKey;
  }

  public void onAcknowledgement(RecordMetadata recordMetadata, Exception exception) {
    // Do nothing
  }

  public void close() {
    // Do nothing
  }

  public void configure(Map<String, ?> configs) {
    try {
      VaultFactory vault = new VaultFactory(configs);
      transit = vault.transit;
      configuration = vault.configuration;
      lifeTimeMillis = configuration.getLongOrDefault(TRANSIT_KEY_TTL_CONFIG, TRANSIT_KEY_TTL_DEFAULT);
      defaultKey = configuration.getStringOrDefault(TRANSIT_KEY_CONFIG, TRANSIT_KEY_DEFAULT);
    } catch (Exception ignored) {
      LOGGER.error("Failed to create Vault Client");
    }
    try {
      valueSerializer = (Serializer) Class.forName(configuration.getString("interceptor.value.serializer")).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      LOGGER.error("Failed to create instance of interceptor.value.serializer", e);
    }
  }
}

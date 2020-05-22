package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.protocol.types.Field;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.*;

public class EncryptingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  TransitConfiguration configuration;
  Vault vault;
  String mount;
  String defaultKey;
  Serializer<V> valueSerializer;

  public ProducerRecord onSend(ProducerRecord<K, V> record) {
    if (record.value() == null) return record;
    LogicalResponse vaultResponse = null;
    String encryptionKey = extractKeyOrElse(record.key(), defaultKey);
    String encryptPath = String.format("%s/encrypt/%s", mount, encryptionKey);
    try {
      String base64value = getBase64value(record);
      vaultResponse = vault.logical().write(
        encryptPath,
        Collections.<String, Object>singletonMap("plaintext", base64value));
      if (vaultResponse.getRestResponse().getStatus() == 200) {
        String encryptedData = vaultResponse.getData().get("ciphertext");
        Headers headers = record.headers();
        headers.add("x-vault-encryption-key", encryptionKey.getBytes());
        return new ProducerRecord<K, String>(
          record.topic(),
          record.partition(),
          record.timestamp(),
          record.key(),
          encryptedData,
          headers
        );
      } else {
        LOGGER.error(String.format("Encryption failed with status code: %d", vaultResponse.getRestResponse().getStatus()));
        throw new RuntimeException("Encryption failed");
      }
    } catch (VaultException e) {
      LOGGER.error("Failed to encrypt records Vault", e);
      throw new RuntimeException("Failed to encrypt records Vault");
    }
  }

  private String getBase64value(ProducerRecord<K, V> record) {
    return Base64.getEncoder().encodeToString(valueSerializer.serialize(record.topic(), record.value()));
  }

  private String extractKeyOrElse(K key, String defaultKey) {
     if(key instanceof String) {
       return (String) key;
    } else return defaultKey;
  }

  public void onAcknowledgement(RecordMetadata recordMetadata, Exception exception) {
    // Do nothing
  }

  public void close() {
    // Do nothing
  }

  public void configure(Map<String, ?> configs) {
    configuration = new TransitConfiguration(configs);
    try {
      valueSerializer = (Serializer) Class.forName(configuration.getString("interceptor.value.serializer")).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      LOGGER.error("Failed to create instance of interceptor.value.serializer", e);
    }
    vault = new VaultFactory(configuration).vault;
    mount = configuration.getStringOrDefault(TRANSIT_MOUNT_CONFIG, TRANSIT_MOUNT_DEFAULT);
    defaultKey = configuration.getStringOrDefault(TRANSIT_KEY_CONFIG, TRANSIT_KEY_DEFAULT);
  }
}

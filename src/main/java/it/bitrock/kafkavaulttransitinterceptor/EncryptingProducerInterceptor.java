package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.*;

public class EncryptingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

  TransitConfiguration configuration;
  Vault vault;
  String mount;
  String key;
  String encryptPath;
  Serializer<V> valueSerializer;

  public ProducerRecord onSend(ProducerRecord<K, V> record) {
    if (record.value() == null) return record;
    LogicalResponse vaultResponse = null;
    try {
      String base64value = Base64.getEncoder().encodeToString(valueSerializer.serialize(record.topic(), record.value()));
      vaultResponse = vault.logical().write(
        encryptPath,
        Collections.<String, Object>singletonMap("plaintext", base64value));
      if (vaultResponse.getRestResponse().getStatus() == 200) {
        String encryptedData = vaultResponse.getData().get("ciphertext");
        Headers headers = record.headers();
        headers.add("x-vault-encryption-key", key.getBytes());
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
      e.printStackTrace();
      return null;
    }
  }

  public void onAcknowledgement(RecordMetadata recordMetadata, Exception exception) {
    // Do nothing
  }

  public void close() {
    // Do nothing
  }

  public void configure(Map<String, ?> configs) {
    LOGGER.info(configs.toString());
    System.out.println(configs.toString());
    configuration = new TransitConfiguration(configs);
    try {
      valueSerializer = (Serializer) Class.forName(configuration.getStringOrDefault("value.serializer", "null")).newInstance();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    vault = new VaultFactory(configuration).vault;
    mount = configuration.getStringOrDefault(TRANSIT_MOUNT_CONFIG, TRANSIT_MOUNT_DEFAULT);
    key = configuration.getStringOrDefault(TRANSIT_KEY_CONFIG, TRANSIT_KEY_DEFAULT);
    encryptPath = String.format("%s/encrypt/%s", mount, key);
  }
}

package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.util.Collections;
import java.util.Map;

public class EncryptingProducerInterceptor implements ProducerInterceptor {
    private Vault vault = VaultFactory.getInstance();

    public ProducerRecord onSend(ProducerRecord producerRecord) {
        String mount = getTransitMount(producerRecord);
        String key = getEncryptionKey(producerRecord);
        LogicalResponse vaultResponse = null;
        try {
            vaultResponse = vault.logical().write(
                    String.format("{}/encrypt/{}", mount, key),
                    Collections.<String, Object>singletonMap("plaintext", producerRecord.value()));
            if (vaultResponse.getRestResponse().getStatus() == 200) {
                String encryptedData = vaultResponse.getData().get("ciphertext");
                Headers headers = producerRecord.headers();
                headers.add("x-vault-encryption-key", key.getBytes());
                ProducerRecord<Object, String> encryptedProducerRecord = new ProducerRecord<Object, String>(
                        producerRecord.topic(),
                        producerRecord.partition(),
                        producerRecord.timestamp(),
                        producerRecord.key(),
                        encryptedData,
                        headers
                );
                return encryptedProducerRecord;
            } else {
                throw new RuntimeException("Encryption failed");
                return null;
            }
        } catch (VaultException e) {
            e.printStackTrace();
            return null
        }
    }

    private String getTransitMount(ProducerRecord producerRecord) {
        return "transit";
    }

    private String getEncryptionKey(ProducerRecord producerRecord) {
        return "default";
    }

    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
        // todo: nothing to do here?

    }

    public void close() {
        // todo: nothing to do here?

    }

    public void configure(Map<String, ?> map) {
        // todo: nothing to do here?

    }
}

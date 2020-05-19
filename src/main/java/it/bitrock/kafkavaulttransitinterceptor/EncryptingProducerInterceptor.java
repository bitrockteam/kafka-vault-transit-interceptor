package it.bitrock.kafkavaulttransitinterceptor;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

public class EncryptingProducerInterceptor implements ProducerInterceptor {
    public ProducerRecord onSend(ProducerRecord producerRecord) {
        // encrypt and return ciphertext data
        return null;
    }

    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {

    }

    public void close() {

    }

    public void configure(Map<String, ?> map) {

    }
}

package it.bitrock.kafkavaulttransitinterceptor;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.util.Map;

public class DecryptingConsumerInterceptor implements ConsumerInterceptor {
    public ConsumerRecords onConsume(ConsumerRecords consumerRecords) {
        // decrypt and return plaintext data
        return null;
    }

    public void close() {

    }

    public void onCommit(Map map) {

    }

    public void configure(Map<String, ?> map) {

    }
}

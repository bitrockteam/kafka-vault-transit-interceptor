package it.bitrock.kafkavaulttransitinterceptor;

import kafka.tools.DefaultMessageFormatter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;


public class HeaderMessageFormat extends DefaultMessageFormatter {
    byte[] headersSeparator = utfBytes(",");
    StringDeserializer stringDeserializer = new StringDeserializer();
    IntegerDeserializer integerDeserializer = new IntegerDeserializer();

    @Override
    public void writeTo(ConsumerRecord<byte[], byte[]> consumerRecord, PrintStream output) {
        write(utfBytes("Headers: ["), output);
        Iterator<Header> headersIt = consumerRecord.headers().iterator();
        if (headersIt.hasNext()) {
            while(headersIt.hasNext()){
                Header header = headersIt.next();
                write(utfBytes(header.key() + ":"), output);
                if(Objects.equals(header.key(), "x-vault-encryption-key-version")){
                    write(deserialize(integerDeserializer, header.value(), consumerRecord), output);
                } else {
                    write(deserialize(stringDeserializer, header.value(), consumerRecord), output);
                }
                if (headersIt.hasNext()) {
                    write(headersSeparator, output);
                }
            }
        } else {
            write(utfBytes("NO_HEADERS"), output);
        }
        write(utfBytes("]"), output);
        write(super.keySeparator(), output);
        super.writeTo(consumerRecord, output);
    }

    private byte[] utfBytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] deserialize(Deserializer deserializer, byte[] sourceBytes, ConsumerRecord<byte[], byte[]> consumerRecord ) {
        return utfBytes(deserializer.deserialize(consumerRecord.topic(), consumerRecord.headers(), sourceBytes).toString());
    }

    private void write(byte[] data, PrintStream output){
        try {
            output.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


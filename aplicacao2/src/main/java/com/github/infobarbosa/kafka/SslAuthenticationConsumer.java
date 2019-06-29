package com.github.infobarbosa.kafka;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

public class SslAuthenticationConsumer {
    private static final Logger logger = LoggerFactory.getLogger(SslAuthenticationConsumer.class.getName());

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1.infobarbosa.github.com:9093");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "consumer-tutorial");
        properties.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "100");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-tutorial-group");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        properties.put("security.protocol", "SSL");
        properties.put("ssl.truststore.location", "/home/vagrant/ssl/kafka.client.truststore.jks");
        properties.put("ssl.truststore.password", "senha-insegura");

        properties.put("ssl.keystore.location", "/home/vagrant/ssl/kafka.client.keystore.jks");
        properties.put("ssl.keystore.password", "senha-insegura");
        properties.put("ssl.key.password", "senha-insegura");

        final String topic = "teste";

        try {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);

            consumer.subscribe(Arrays.asList(topic));
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                for (ConsumerRecord<String, String> record : records) {
                    String key = record.key();
                    String value = record.value();
                    long offset = record.offset();
                    long partition = record.partition();
                    long timestamp = record.timestamp();

                    consumer.commitAsync(new OffsetCommitCallback() {
                        @Override
                        public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception exception) {
                            logger.info("K: " + key
                                    + "; V: " + value
                                    + "; TS: " + timestamp
                            );
                        }
                    });

                    //coloca pra dormir um pouco
                    try {
                        Thread.sleep(100);
                    }
                    catch(InterruptedException e){
                        logger.error("problemas durante o sono.", e);
                    }
                }
            }
        }
        catch(Exception e){
            logger.error("Problemas durante o consumo", e);
        }
    }
}

package ewm.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import serialization.GeneralAvroSerializer;
import org.apache.kafka.common.serialization.LongSerializer;

import java.util.Properties;

@Configuration
public class KafkaClientConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    //@Bean
    //@Scope("prototype")
    @Bean(destroyMethod = "stop")
    public KafkaClient getKafkaClient() {
        return new KafkaClient() {

            private Producer<Long, SpecificRecordBase> producer;

            @Override
            public synchronized Producer<Long, SpecificRecordBase> getProducer() {
                if (producer == null) {
                    initProducer();
                }
                return producer;
            }

            private void initProducer() {
                Properties config = new Properties();

                config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
                config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GeneralAvroSerializer.class);
                config.put(ProducerConfig.ACKS_CONFIG, "all");//Producer ждёт подтверждения от всех синхронизированных реплик
                producer = new KafkaProducer<>(config);
            }

            @Override
            public synchronized void stop() {
                if (producer != null) {
                    producer.close();
                }
            }
        };
    }
}

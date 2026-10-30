package ru.practicum.ewm.collector.service;

import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionProto;

@Service
public class UserActionService {
    private final KafkaProducer<String, byte[]> producer;
    private final UserActionMapper mapper;
    private final AvroSerializer serializer;
    private final String topic;

    public UserActionService(KafkaProducer<String, byte[]> producer,
                             UserActionMapper mapper,
                             AvroSerializer serializer,
                             @Value("${collector.kafka.topics.user-actions}") String topic) {
        this.producer = producer;
        this.mapper = mapper;
        this.serializer = serializer;
        this.topic = topic;
    }

    public void collect(UserActionProto action) {
        UserActionAvro avroAction = mapper.toAvro(action);
        byte[] payload = serializer.serialize(avroAction);
        try {
            producer.send(new ProducerRecord<>(topic, String.valueOf(action.getUserId()), payload)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing user action", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to publish user action to Kafka", exception.getCause());
        }
    }
}

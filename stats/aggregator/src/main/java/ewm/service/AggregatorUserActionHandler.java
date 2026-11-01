package ewm.service;

import ewm.model.EventSimilarity;
import kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.ArrayList;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatorUserActionHandler implements UserActionHandler {

    private final SimilarityCalculator similarityCalculator;
    private final Producer<String, SpecificRecordBase> producer;

    @Override
    public void handle(UserActionAvro userAction) {
        List<EventSimilarity> similarities = similarityCalculator.process(userAction);

        log.info(
                "Обработка действия: eventId={}, userId={}, actionType={}, timestamp={}",
                userAction.getEventId(),
                userAction.getUserId(),
                userAction.getActionType(),
                userAction.getTimestamp()
        );

        log.info("Количество рассчитанных пар: {}", similarities.size());

        List<Future<RecordMetadata>> futures = new ArrayList<>();

        for (EventSimilarity similarity : similarities) {
            futures.add(publish(similarity));
        }

        List<String> errors = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                RecordMetadata metadata = futures.get(i).get();

                EventSimilarity similarity = similarities.get(i);

                log.info(
                        "Опубликовано: eventA={}, eventB={}, partition={}, offset={}",
                        similarity.eventA(),
                        similarity.eventB(),
                        metadata.partition(),
                        metadata.offset()
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Прервано ожидание отправки сходства мероприятий", e
                );

            } catch (ExecutionException e) {
                EventSimilarity similarity = similarities.get(i);

                String error = String.format(
                        "eventA=%d, eventB=%d: %s",
                        similarity.eventA(),
                        similarity.eventB(),
                        e.getCause().getMessage()
                );

                errors.add(error);

                log.error("Ошибка публикации: {}", error, e);
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Не удалось опубликовать некоторые сходства: " + errors
            );
        }
    }

    private Future<RecordMetadata> publish(EventSimilarity similarity) {
        EventSimilarityAvro event = EventSimilarityAvro.newBuilder()
                .setEventA(similarity.eventA())
                .setEventB(similarity.eventB())
                .setScore(similarity.score())
                .setTimestamp(similarity.timestamp())
                .build();

        String key = similarity.eventA() + "_" + similarity.eventB();

        ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(
                KafkaTopics.EVENTS_SIMILARITY,
                key,
                event
        );

        return producer.send(record);
    }
}

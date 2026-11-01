package ewm.kafka;

import ewm.service.EventSimilarityService;
import ewm.service.UserActionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import kafka.KafkaTopics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzerKafkaConsumer {

    private final Consumer<String, UserActionAvro> userActionKafkaConsumerClient;
    private final Consumer<String, EventSimilarityAvro> eventSimilarityKafkaConsumerClient;

    private final UserActionService userActionService;
    private final EventSimilarityService eventSimilarityService;

    @Value("${analyzer.kafka.consumer.poll-timeout:100ms}")
    private Duration pollTimeout;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService executor;

    @PostConstruct
    public void start() {
        running.set(true);

        executor = Executors.newFixedThreadPool(2);

        executor.submit(this::consumeUserActions);
        executor.submit(this::consumeEventSimilarities);

        log.info("Kafka Consumers Analyzer запущены");
    }

    private void consumeUserActions() {
        consume(
                userActionKafkaConsumerClient,
                KafkaTopics.USER_ACTIONS,
                userActionService::process
        );
    }

    private void consumeEventSimilarities() {
        consume(
                eventSimilarityKafkaConsumerClient,
                KafkaTopics.EVENTS_SIMILARITY,
                eventSimilarityService::process
        );
    }

    private <T> void consume(
            Consumer<String, T> consumer,
            String topic,
            java.util.function.Consumer<T> handler
    ) {
        try {
            consumer.subscribe(List.of(topic));

            while (running.get()) {
                ConsumerRecords<String, T> records = consumer.poll(pollTimeout);

                if (records.isEmpty()) {
                    continue;
                }

                for (ConsumerRecord<String, T> record : records) {
                    handler.accept(record.value());
                }

                consumer.commitSync();

                log.debug("Топик {}: обработано {} сообщений", topic, records.count());
            }
        } catch (WakeupException e) {
            if (running.get()) {
                throw e;
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщений из топика {}", topic, e);
        } finally {
            consumer.close();
            log.info("Kafka Consumer для топика {} остановлен", topic);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        if (executor == null) {
            return;
        }

        userActionKafkaConsumerClient.wakeup();
        eventSimilarityKafkaConsumerClient.wakeup();

        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Kafka Consumers Analyzer остановлены");
    }
}

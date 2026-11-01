package ewm.kafka;

import ewm.service.UserActionHandler;
import kafka.KafkaTopics;
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
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregatorKafkaConsumer {

    private final Consumer<String, UserActionAvro> consumer;
    private final UserActionHandler userActionHandler;

    @Value("${aggregator.kafka.consumer.poll-timeout:100ms}")
    private Duration pollTimeout;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService executor;

    @PostConstruct
    public void start() {
        running.set(true);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(this::consume);
    }

    private void consume() {
        try {
            consumer.subscribe(java.util.List.of(KafkaTopics.USER_ACTIONS));

            while (running.get()) {
                ConsumerRecords<String, UserActionAvro> records =
                        consumer.poll(pollTimeout);

                if (records.isEmpty()) {
                    continue;
                }

                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    userActionHandler.handle(record.value());
                }

                consumer.commitSync();

                log.debug("Обработано {} пользовательских действий", records.count());
            }
        } catch (WakeupException e) {
            if (running.get()) {
                throw e;
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке пользовательских действий", e);
        } finally {
            consumer.close();
            log.info("Kafka Consumer остановлен");
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        if (executor == null) {
            return;
        }

        consumer.wakeup();
        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

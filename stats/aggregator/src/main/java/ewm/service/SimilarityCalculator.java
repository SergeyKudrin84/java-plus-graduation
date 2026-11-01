package ewm.service;


import ewm.model.EventPair;
import ewm.model.EventSimilarity;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.Instant;

@Service
public class SimilarityCalculator {

    private static final Map<String, Double> ACTION_WEIGHTS = Map.of(
            "VIEW", 0.4,
            "REGISTER", 0.8,
            "LIKE", 1.0
    );

    // eventId -> (userId -> максимальный вес действия)
    private final Map<Long, Map<Long, Double>> eventUserWeights = new HashMap<>();

    // eventId -> сумма максимальных весов пользователей
    private final Map<Long, Double> eventWeightSums = new HashMap<>();

    // eventA -> (eventB -> сумма минимумов)
    private final Map<Long, Map<Long, Double>> minimumWeightSums = new HashMap<>();

    public List<EventSimilarity> process(UserActionAvro action) {
        long eventId = action.getEventId();
        long userId = action.getUserId();
        double newWeight = getWeight(action);

        Set<Long> knownEvents = new HashSet<>(eventUserWeights.keySet());

        Map<Long, Double> userWeights = eventUserWeights.computeIfAbsent(
                eventId,
                id -> new HashMap<>()
        );

        boolean hasPreviousAction = userWeights.containsKey(userId);

        double oldWeight = userWeights.getOrDefault(userId, 0.0);

        if (hasPreviousAction && newWeight <= oldWeight) {
            return List.of();
        }

        double actualWeight = Math.max(oldWeight, newWeight);

        // Сохраняем максимальный вес пользователя для мероприятия.
        userWeights.put(userId, actualWeight);

        // Обновляем сумму весов мероприятия.
        eventWeightSums.merge(
                    eventId,
                    actualWeight - oldWeight,
                    Double::sum
            );

        // Обновляем суммы минимумов для мероприятий,
        // с которыми взаимодействовал тот же пользователь.
        updateMinimumWeightSums(
                eventId,
                userId,
                oldWeight,
                actualWeight,
                knownEvents
        );

        // Новое мероприятие сравниваем со всеми известными.
        // Для существующего мероприятия пересчитываем только его пары.
        return calculateSimilarities(
                eventId,
                userId,
                knownEvents,
                action.getTimestamp()
        );
    }

    private void updateMinimumWeightSums(
            long eventId,
            long userId,
            double oldWeight,
            double newWeight,
            Set<Long> knownEvents
    ) {
        for (Long otherEventId : knownEvents) {
            if (otherEventId.equals(eventId)) {
                continue;
            }
            Map<Long, Double> otherUserWeights = eventUserWeights.get(otherEventId);

            Double otherWeight = otherUserWeights.get(userId);

            if (otherWeight == null) {
                continue;
            }

            double delta = Math.min(newWeight, otherWeight)
                    - Math.min(oldWeight, otherWeight);

            if (delta <= 0.0) {
                continue;
            }

            EventPair pair = new EventPair(eventId, otherEventId);

            minimumWeightSums
                    .computeIfAbsent(pair.eventA(), id -> new HashMap<>())
                    .merge(pair.eventB(), delta, Double::sum);
        }
    }

    private List<EventSimilarity> calculateSimilarities(
            long eventId,
            long userId,
            Set<Long> knownEvents,
            Instant timestamp
    ){
        List<EventSimilarity> results = new ArrayList<>();

        for (Long otherEventId : knownEvents) {
            if (otherEventId.equals(eventId)) {
                continue;
            }

            Map<Long, Double> otherUserWeights = eventUserWeights.get(otherEventId);

            if (!otherUserWeights.containsKey(userId)) {
                continue;
            }

            EventPair pair = new EventPair(eventId, otherEventId);

            double minimumSum = minimumWeightSums
                    .getOrDefault(pair.eventA(), Map.of())
                    .getOrDefault(pair.eventB(), 0.0);

            if (minimumSum <= 0.0) {
                continue;
            }

            double firstSum = eventWeightSums.getOrDefault(eventId, 0.0);
            double secondSum = eventWeightSums.getOrDefault(otherEventId, 0.0);

            double denominator = Math.sqrt(firstSum) * Math.sqrt(secondSum);

            double score = minimumSum / denominator;

            results.add(new EventSimilarity(
                    pair.eventA(),
                    pair.eventB(),
                    score,
                    timestamp
            ));
        }

        return results;
    }

    private double getWeight(UserActionAvro action) {
        Double weight = ACTION_WEIGHTS.get(action.getActionType().name());

        if (weight == null) {
            throw new IllegalArgumentException(
                    "Неизвестный тип действия: " + action.getActionType()
            );
        }

        return weight;
    }
}

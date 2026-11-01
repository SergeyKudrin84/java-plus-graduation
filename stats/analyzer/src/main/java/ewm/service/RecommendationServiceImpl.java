package ewm.service;

import ewm.model.Interaction;
import ewm.model.Similarity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.service.dashboard.RecommendedEventProto;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final UserActionService userActionService;
    private final EventSimilarityService eventSimilarityService;

    @Value("${analyzer.recommendations.neighbors-limit:10}")
    private int neighborsLimit;

    @Override
    @Transactional(readOnly = true)
    public List<RecommendedEventProto> getRecommendationsForUser(
            Long userId,
            int maxResults
    ) {
        if (maxResults <= 0) {
            return List.of();
        }

        // Получаем последние N взаимодействий пользователя.
        List<Interaction> recentInteractions =
                userActionService.getRecentInteractions(userId, maxResults);

        if (recentInteractions.isEmpty()) {
            return List.of();
        }

        // Получаем ID мероприятий, с которыми пользователь взаимодействовал.
        List<Long> interactedEventIds = recentInteractions.stream()
                .map(Interaction::getEventId)
                .toList();

        // Получаем взаимодействия пользователя со всеми выбранными мероприятиями.
        List<Interaction> interactions =
                userActionService.getInteractions(userId, interactedEventIds);

        // Создаём карту: ID мероприятия → оценка пользователя.
        Map<Long, Float> ratingsByEventId = interactions.stream()
                .collect(Collectors.toMap(
                        Interaction::getEventId,
                        Interaction::getRating
                ));

        // Получаем сходство исходных мероприятий с другими.
        List<Similarity> similarities =
                eventSimilarityService.findSimilarities(interactedEventIds);

        // Исключаем уже просмотренные мероприятия.
        // Для каждого кандидата сохраняем максимальный коэффициент сходства.
        Set<Long> interactedEventIdSet = new HashSet<>(interactedEventIds);
        Map<Long, Float> candidateScores = new HashMap<>();

        for (Similarity similarity : similarities) {
            boolean event1Interacted =
                    interactedEventIdSet.contains(similarity.getEvent1());

            boolean event2Interacted =
                    interactedEventIdSet.contains(similarity.getEvent2());

            // Оба мероприятия уже известны пользователю либо ни одно не подходит.
            if (event1Interacted == event2Interacted) {
                continue;
            }

            long candidateId = event1Interacted
                    ? similarity.getEvent2()
                    : similarity.getEvent1();

            candidateScores.merge(
                    candidateId,
                    similarity.getSimilarity(),
                    Math::max
            );
        }

        // Сортируем кандидатов по максимальному сходству.
        return candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Float>comparingByValue().reversed())
                .limit(maxResults)
                // Рассчитываем прогнозируемую оценку каждого кандидата.
                .map(entry -> calculatePredictedScore(
                        entry.getKey(),
                        interactedEventIds,
                        ratingsByEventId
                ).map(score -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(score)
                        .build()))
                // Исключаем кандидатов, для которых прогноз рассчитать невозможно.
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Float> calculatePredictedScore(
            Long candidateEventId,
            List<Long> interactedEventIds,
            Map<Long, Float> ratingsByEventId
    ) {
        // Находим K ближайших мероприятий из истории пользователя.
        List<Similarity> neighbors = eventSimilarityService.findSimilarities(
                candidateEventId,
                interactedEventIds,
                neighborsLimit
        );

        if (neighbors.isEmpty()) {
            return Optional.empty();
        }

        double weightedRatingSum = 0.0;
        double similaritySum = 0.0;

        for (Similarity neighbor : neighbors) {
            long neighborEventId = neighbor.getEvent1().equals(candidateEventId)
                    ? neighbor.getEvent2()
                    : neighbor.getEvent1();

            Float rating = ratingsByEventId.get(neighborEventId);

            if (rating == null) {
                continue;
            }

            double similarity = neighbor.getSimilarity();

            weightedRatingSum += similarity * rating;
            similaritySum += similarity;
        }

        if (similaritySum == 0.0) {
            return Optional.empty();
        }

        return Optional.of((float) (weightedRatingSum / similaritySum));
    }
}
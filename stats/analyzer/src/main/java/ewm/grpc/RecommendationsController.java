package ewm.grpc;

import ewm.repository.InteractionCountProjection;
import ewm.service.InteractionService;
import ewm.service.RecommendationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.stats.service.dashboard.InteractionsCountRequestProto;
import ru.practicum.stats.service.dashboard.RecommendedEventProto;
import ru.practicum.stats.service.dashboard.RecommendationsControllerGrpc;

import ewm.model.Similarity;
import ewm.service.EventSimilarityService;
import ru.practicum.stats.service.dashboard.SimilarEventsRequestProto;
import ru.practicum.stats.service.dashboard.UserPredictionsRequestProto;

import java.util.List;

@GrpcService
@RequiredArgsConstructor
public class RecommendationsController
        extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final InteractionService interactionService;
    private final EventSimilarityService eventSimilarityService;
    private final RecommendationService recommendationService;

    @Override
    public void getInteractionsCount(
            InteractionsCountRequestProto request,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        List<InteractionCountProjection> interactions =
                interactionService.getInteractionsCount(request.getEventIdList());

        interactions.forEach(interaction -> {
            RecommendedEventProto response = RecommendedEventProto.newBuilder()
                    .setEventId(interaction.getEventId())
                    .setScore(interaction.getScore())
                    .build();

            responseObserver.onNext(response);
        });

        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarEvents(
            SimilarEventsRequestProto request,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        if (maxResults <= 0) {
            responseObserver.onCompleted();
            return;
        }

        // Получаем все пары сходства для исходного мероприятия.
        List<Similarity> similarities =
                eventSimilarityService.findSimilarities(eventId);

        // Из каждой пары извлекаем ID другого мероприятия.
        List<Similarity> candidates = similarities.stream()
                .filter(similarity ->
                        similarity.getEvent1().equals(eventId)
                                || similarity.getEvent2().equals(eventId))
                .filter(similarity ->
                        !similarity.getEvent1().equals(similarity.getEvent2()))
                .toList();

        List<Long> candidateEventIds = candidates.stream()
                .map(similarity -> similarity.getEvent1().equals(eventId)
                        ? similarity.getEvent2()
                        : similarity.getEvent1())
                .toList();

        // Получаем мероприятия, с которыми пользователь уже взаимодействовал.
        List<Long> interactedEventIds =
                interactionService.getInteractedEventIds(userId, candidateEventIds);

        // Исключаем их, сортируем по сходству и ограничиваем результат.
        candidates.stream()
                .filter(similarity -> {
                    long candidateId = similarity.getEvent1().equals(eventId)
                            ? similarity.getEvent2()
                            : similarity.getEvent1();

                    return !interactedEventIds.contains(candidateId);
                })
                .sorted((first, second) ->
                        Float.compare(second.getSimilarity(), first.getSimilarity()))
                .limit(maxResults)
                .forEach(similarity -> {
                    long candidateId = similarity.getEvent1().equals(eventId)
                            ? similarity.getEvent2()
                            : similarity.getEvent1();

                    RecommendedEventProto response = RecommendedEventProto.newBuilder()
                            .setEventId(candidateId)
                            .setScore(similarity.getSimilarity())
                            .build();

                    responseObserver.onNext(response);
                });

        responseObserver.onCompleted();
    }

    @Override
    public void getRecommendationsForUser(
            UserPredictionsRequestProto request,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        List<RecommendedEventProto> recommendations =
                recommendationService.getRecommendationsForUser(userId, maxResults);

        recommendations.forEach(responseObserver::onNext);

        responseObserver.onCompleted();
    }
}

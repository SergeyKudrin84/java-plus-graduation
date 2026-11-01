package ewm.service;

import ru.practicum.stats.service.dashboard.RecommendedEventProto;

import java.util.List;

public interface RecommendationService {
    List<RecommendedEventProto> getRecommendationsForUser(
            Long userId,
            int maxResults
    );
}

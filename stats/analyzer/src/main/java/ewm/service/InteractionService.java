package ewm.service;

import ewm.model.Interaction;
import ewm.repository.InteractionCountProjection;

import java.util.List;

public interface InteractionService {

    List<InteractionCountProjection> getInteractionsCount(List<Long> eventIds);

    List<Long> getInteractedEventIds(Long userId, List<Long> eventIds);

    List<Interaction> getRecentInteractions(Long userId, int count);

    List<Interaction> getInteractions(Long userId, List<Long> eventIds);
}

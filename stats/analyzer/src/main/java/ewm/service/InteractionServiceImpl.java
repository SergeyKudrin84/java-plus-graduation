package ewm.service;

import ewm.model.Interaction;
import ewm.repository.InteractionCountProjection;
import ewm.repository.InteractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    private final InteractionRepository interactionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InteractionCountProjection> getInteractionsCount(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        return interactionRepository.findInteractionCounts(eventIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getInteractedEventIds(Long userId, List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        return interactionRepository.findEventIdsByUserIdAndEventIdIn(userId, eventIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Interaction> getRecentInteractions(Long userId, int count) {
        if (count <= 0) {
            return List.of();
        }

        return interactionRepository.findByUserIdOrderByTsDesc(
                userId,
                PageRequest.of(0, count)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Interaction> getInteractions(Long userId, List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        return interactionRepository.findByUserIdAndEventIdIn(userId, eventIds);
    }
}

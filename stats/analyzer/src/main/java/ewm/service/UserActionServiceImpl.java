package ewm.service;

import ewm.model.Interaction;
import ewm.repository.InteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionServiceImpl implements UserActionService {

    private static final Map<String, Float> ACTION_WEIGHTS = Map.of(
            "VIEW", 0.4f,
            "REGISTER", 0.8f,
            "LIKE", 1.0f
    );

    private final InteractionService interactionService;

    private final InteractionRepository interactionRepository;

    @Override
    @Transactional
    public void process(UserActionAvro action) {
        Float newRating = getRating(action);

        Interaction interaction = interactionRepository
                .findByUserIdAndEventId(action.getUserId(), action.getEventId())
                .orElseGet(() -> Interaction.builder()
                        .userId(action.getUserId())
                        .eventId(action.getEventId())
                        .rating(0.0f)
                        .build());

        if (newRating > interaction.getRating()) {
            interaction.setRating(newRating);
        }

        interaction.setTs(action.getTimestamp().atOffset(ZoneOffset.UTC));

        interactionRepository.save(interaction);

        log.debug("Обновлено взаимодействие: userId={}, eventId={}, rating={}",
                action.getUserId(),
                action.getEventId(),
                newRating);
    }

    private Float getRating(UserActionAvro action) {
        Float rating = ACTION_WEIGHTS.get(action.getActionType().name());

        if (rating == null) {
            throw new IllegalArgumentException(
                    "Неизвестный тип действия: " + action.getActionType()
            );
        }

        return rating;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Interaction> getRecentInteractions(Long userId, int count) {
        return interactionService.getRecentInteractions(userId, count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Interaction> getInteractions(Long userId, List<Long> eventIds) {
        return interactionService.getInteractions(userId, eventIds);
    }
}

package ewm.service;

import ewm.model.Similarity;
import ewm.repository.SimilarityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSimilarityServiceImpl implements EventSimilarityService {

    private final SimilarityRepository similarityRepository;

    @Override
    @Transactional
    public void process(EventSimilarityAvro eventSimilarity) {
        long event1 = eventSimilarity.getEventA();
        long event2 = eventSimilarity.getEventB();

        if (event1 == event2) {
            log.warn("Получена пара с одинаковыми eventId: {}", event1);
            return;
        }

        // Нормализуем пару: меньший ID всегда первый.
        if (event1 > event2) {
            long temp = event1;
            event1 = event2;
            event2 = temp;
        }

        final long normalizedEvent1 = event1;
        final long normalizedEvent2 = event2;

        Similarity similarity = similarityRepository
                .findByEvent1AndEvent2(event1, event2)
                .orElseGet(() -> Similarity.builder()
                        .event1(normalizedEvent1)
                        .event2(normalizedEvent2)
                        .build());

        similarity.setSimilarity((float) eventSimilarity.getScore());
        similarity.setTs(eventSimilarity.getTimestamp().atOffset(ZoneOffset.UTC));

        similarityRepository.save(similarity);

        log.debug("Обновлено сходство мероприятий: event1={}, event2={}, similarity={}",
                event1,
                event2,
                similarity.getSimilarity());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Similarity> findSimilarities(Long eventId) {
        return similarityRepository.findByEvent1OrEvent2(eventId, eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Similarity> findSimilarities(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        return similarityRepository.findByEvent1InOrEvent2In(
                eventIds,
                eventIds
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Similarity> findSimilarities(
            Long eventId,
            List<Long> eventIds,
            int count
    ) {
        if (eventIds == null || eventIds.isEmpty() || count <= 0) {
            return List.of();
        }

        return similarityRepository.findByEvent1AndEvent2InOrEvent2AndEvent1In(
                eventId,
                eventIds,
                eventId,
                eventIds,
                PageRequest.of(
                        0,
                        count,
                        Sort.by(Sort.Direction.DESC, "similarity")
                )
        );
    }
}

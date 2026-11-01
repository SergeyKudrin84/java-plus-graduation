package ewm.service;

import ewm.model.Similarity;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.util.List;

public interface EventSimilarityService {

    void process(EventSimilarityAvro eventSimilarity);

    List<Similarity> findSimilarities(Long eventId);

    List<Similarity> findSimilarities(List<Long> eventIds);

    List<Similarity> findSimilarities(Long eventId, List<Long> eventIds, int count);
}
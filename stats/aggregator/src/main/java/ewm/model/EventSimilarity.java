package ewm.model;

import java.time.Instant;

public record EventSimilarity(
        long eventA,
        long eventB,
        double score,
        Instant timestamp
) {
    public EventSimilarity {
        if (eventA > eventB) {
            long temp = eventA;
            eventA = eventB;
            eventB = temp;
        }
    }
}

package ewm.repository;

import ewm.model.Similarity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimilarityRepository extends JpaRepository<Similarity, Long> {

    Optional<Similarity> findByEvent1AndEvent2(Long event1, Long event2);

    List<Similarity> findByEvent1OrEvent2(Long event1, Long event2);

    List<Similarity> findByEvent1InOrEvent2In(
            List<Long> event1Ids,
            List<Long> event2Ids
    );

    List<Similarity> findByEvent1AndEvent2InOrEvent2AndEvent1In(
            Long eventId1,
            List<Long> eventIds1,
            Long eventId2,
            List<Long> eventIds2,
            Pageable pageable
    );
}

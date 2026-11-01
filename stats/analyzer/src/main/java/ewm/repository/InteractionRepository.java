package ewm.repository;

import ewm.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    Optional<Interaction> findByUserIdAndEventId(Long userId, Long eventId);

    @Query("""
        SELECT i.eventId AS eventId,
               SUM(i.rating) AS score
        FROM Interaction i
        WHERE i.eventId IN :eventIds
        GROUP BY i.eventId
        """)
    List<InteractionCountProjection> findInteractionCounts(
            @Param("eventIds") List<Long> eventIds
    );

    @Query("""
        SELECT i.eventId
        FROM Interaction i
        WHERE i.userId = :userId
          AND i.eventId IN :eventIds
        """)
    List<Long> findEventIdsByUserIdAndEventIdIn(
            @Param("userId") Long userId,
            @Param("eventIds") List<Long> eventIds
    );

    List<Interaction> findByUserIdOrderByTsDesc(
            Long userId,
            Pageable pageable
    );

    List<Interaction> findByUserIdAndEventIdIn(
            Long userId,
            List<Long> eventIds
    );
}

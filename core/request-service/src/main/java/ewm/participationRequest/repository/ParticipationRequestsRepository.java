package ewm.participationRequest.repository;

import ewm.participationRequest.dto.EventRequestsCountDto;
import ewm.participationRequest.model.ParticipationRequest;
import ewm.participationRequest.model.ParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestsRepository
        extends JpaRepository<ParticipationRequest, Long> {

    @Query("""
            select new ewm.participationRequest.dto.EventRequestsCountDto(
                r.eventId,
                count(r.id)
            )
            from ParticipationRequest r
            where r.status = ewm.participationRequest.model.ParticipationStatus.CONFIRMED
              and r.eventId in :eventIds
            group by r.eventId
            """)
    List<EventRequestsCountDto> getConfirmedRequests(@Param("eventIds") List<Long> eventIds);

    //@EntityGraph(attributePaths = {"event"})
    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    //@EntityGraph(attributePaths = {"event"})
    Optional<ParticipationRequest> findByIdAndRequesterId(Long id, Long requesterId);

    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    long countByEventIdAndStatus(Long eventId, ParticipationStatus status);

    //@EntityGraph(attributePaths = {"requester", "event", "event.initiator"})
    List<ParticipationRequest> findByRequesterId(Long requesterId);

    //@EntityGraph(attributePaths = {"requester", "event", "event.initiator"})
    //Long countByEventAndStatus(Event event, ParticipationStatus status);

    //boolean existsByRequesterAndEvent(User requester, Event event);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, ParticipationStatus status);
}

package ewm.participationRequest.service;

import ewm.participationRequest.dto.EventRequestStatusUpdateRequest;
import ewm.participationRequest.dto.EventRequestStatusUpdateResult;
import ewm.participationRequest.dto.EventRequestsCountDto;
import ewm.participationRequest.dto.ParticipationRequestDto;

import java.util.List;

public interface ParticipationRequestService {

    List<ParticipationRequestDto> getUserRequests(Long userId);

    ParticipationRequestDto addParticipationRequest(Long userId, Long eventId);

    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    List<EventRequestsCountDto> getConfirmedRequests(List<Long> eventIds);

    List<ParticipationRequestDto> getEventRequests(Long eventId);

    EventRequestStatusUpdateResult updateEventRequests(
            Long eventId,
            EventRequestStatusUpdateRequest request
    );

}

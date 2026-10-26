package ewm.participationRequest.controller;

import ewm.participationRequest.dto.EventRequestStatusUpdateRequest;
import ewm.participationRequest.dto.EventRequestStatusUpdateResult;
import ewm.participationRequest.dto.EventRequestsCountDto;
import ewm.participationRequest.dto.ParticipationRequestDto;
import ewm.participationRequest.service.ParticipationRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class InternalParticipationRequestsController {

    private final ParticipationRequestService participationRequestService;

    @PostMapping("/confirmed-counts")
    public List<EventRequestsCountDto> getConfirmedRequests(
            @RequestBody List<Long> eventIds) {
        return participationRequestService.getConfirmedRequests(eventIds);
    }

    @GetMapping("/events/{eventId}")
    public List<ParticipationRequestDto> getEventRequests(
            @PathVariable Long eventId
    ) {
        return participationRequestService.getEventRequests(eventId);
    }

    @PatchMapping("/events/{eventId}")
    public EventRequestStatusUpdateResult updateEventRequests(
            @PathVariable Long eventId,
            @RequestBody EventRequestStatusUpdateRequest request
    ) {
        return participationRequestService.updateEventRequests(eventId, request);
    }
}

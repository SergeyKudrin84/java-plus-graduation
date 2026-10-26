package ewm.feign.client;

import ewm.feign.client.dto.EventRequestStatusUpdateRequest;
import ewm.feign.client.dto.EventRequestStatusUpdateResult;
import ewm.feign.client.dto.EventRequestsCountDto;
import ewm.feign.client.dto.ParticipationRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "main-service",
        path = "/internal/requests",
        fallbackFactory = ParticipationRequestClientFallbackFactory.class
)
public interface ParticipationRequestClient {

    @PostMapping("/confirmed-counts")
    List<EventRequestsCountDto> getConfirmedRequests(
            @RequestBody List<Long> eventIds
    );

    @GetMapping("/events/{eventId}")
    List<ParticipationRequestDto> getEventRequests(
            @PathVariable("eventId") Long eventId
    );

    @PatchMapping("/events/{eventId}")
    EventRequestStatusUpdateResult updateEventRequests(
            @PathVariable("eventId") Long eventId,
            @RequestBody EventRequestStatusUpdateRequest request
    );
}
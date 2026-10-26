package ewm.events.controller;

import ewm.events.dto.EventRequestInfoDto;
import ewm.events.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalEventsController {

    private final EventsService eventsService;

    @GetMapping("/{eventId}/request-info")
    public EventRequestInfoDto getRequestInfo(
            @PathVariable Long eventId
    ) {
        return eventsService.getRequestInfo(eventId);
    }
}

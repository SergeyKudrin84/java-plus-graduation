package ewm.events.controller;

import ewm.events.dto.EventRequestInfoDto;
import ewm.events.dto.EventShortDto;
import ewm.events.repository.EventsRepository;
import ewm.events.service.EventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/categories/{catId}/exists")
    public boolean existsByCategoryId(@PathVariable Long catId) {
        return eventsService.existsByCategoryId(catId);
    }

    @PostMapping("/by-ids")
    public List<EventShortDto> getEventsByIds(
            @RequestBody List<Long> eventIds
    ) {
        return eventsService.getEventsByIds(eventIds);
    }
}

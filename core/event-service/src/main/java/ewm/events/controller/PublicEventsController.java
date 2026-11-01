package ewm.events.controller;

import ewm.events.dto.EventFullDto;
import ewm.events.dto.EventShortDto;
import ewm.events.dto.EventSort;
import ewm.events.dto.params.PublicEventParams;
import ewm.events.service.EventsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@Validated
public class PublicEventsController {

    private final EventsService eventsService;

    @GetMapping("/events")
    public ResponseEntity<List<EventShortDto>> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) EventSort sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
            @RequestParam(defaultValue = "10") @Positive Integer size
    ) {
        log.info("GET /events: text={}, categories={}, paid={}", text, categories, paid);

        PublicEventParams searchParams = PublicEventParams.builder()
                .text(text)
                .categories(categories)
                .paid(paid)
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .onlyAvailable(onlyAvailable)
                .sort(sort)
                .from(from)
                .size(size)
                .build();

        return ResponseEntity.ok(eventsService.getEvents(searchParams));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventFullDto> getEvent(
            @PathVariable Long id,
            @RequestHeader(value = "X-EWM-USER-ID", required = false) Long userId) {
        log.info("GET /events/{}", id);

        return ResponseEntity.ok(eventsService.getEvent(id, userId));
    }

    @GetMapping("/events/recommendations")
    public ResponseEntity<List<EventShortDto>> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") Long userId,
            @RequestParam(defaultValue = "10") @Positive Integer maxResults
    ) {
        log.info("GET /events/recommendations: userId={}, maxResults={}",
                userId, maxResults);

        return ResponseEntity.ok(
                eventsService.getRecommendations(userId, maxResults)
        );
    }

    @PutMapping("/events/{eventId}/like")
    public ResponseEntity<Void> likeEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-EWM-USER-ID") long userId
    ) {
        log.info("PUT /events/{}/like: userId={}", eventId, userId);

        eventsService.likeEvent(eventId, userId);

        return ResponseEntity.ok().build();
    }
}
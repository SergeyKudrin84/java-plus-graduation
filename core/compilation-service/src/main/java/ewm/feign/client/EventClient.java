package ewm.feign.client;

import ewm.feign.client.dto.EventRequestInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "event-service",
        contextId = "eventCompilationClient",
        path = "/internal/events"
)
public interface EventClient {

    @GetMapping("/{eventId}/request-info")
    EventRequestInfoDto getRequestInfo(
            @PathVariable("eventId") Long eventId
    );

    @GetMapping("/categories/{catId}/exists")
    boolean existsByCategoryId(
            @PathVariable("catId") Long catId
    );

    @PostMapping("/by-ids")
    List<EventRequestInfoDto> getEventsByIds(
            @RequestBody List<Long> eventIds
    );
}

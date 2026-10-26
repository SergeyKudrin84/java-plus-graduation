package ewm.feign.client;

import ewm.feign.client.dto.EventRequestInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "event-service",
        path = "/internal/events"
)
public interface EventClient {

    @GetMapping("/{eventId}/request-info")
    EventRequestInfoDto getRequestInfo(
            @PathVariable("eventId") Long eventId
    );
}

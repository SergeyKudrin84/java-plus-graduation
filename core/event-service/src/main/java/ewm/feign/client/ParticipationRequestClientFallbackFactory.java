package ewm.feign.client;

import ewm.feign.client.dto.EventRequestStatusUpdateRequest;
import ewm.feign.client.dto.EventRequestStatusUpdateResult;
import ewm.feign.client.dto.EventRequestsCountDto;
import ewm.feign.client.dto.ParticipationRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Component
public class ParticipationRequestClientFallbackFactory
        implements FallbackFactory<ParticipationRequestClient> {

    private static final Logger log =
            LoggerFactory.getLogger(ParticipationRequestClientFallbackFactory.class);

    @Override
    public ParticipationRequestClient create(Throwable cause) {
        log.error("Не удалось обратиться к main-service для работы с заявками", cause);

        return new ParticipationRequestClient() {
            private ResponseStatusException unavailable() {
                return new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "main-service временно недоступен",
                        cause
                );
            }

            @Override
            public List<EventRequestsCountDto> getConfirmedRequests(List<Long> eventIds) {
                log.warn(
                        "Не удалось получить количество подтверждённых заявок. " +
                                "Возвращаем 0 для событий: {}",
                        eventIds
                );

                return Collections.emptyList();
            }

            @Override
            public List<ParticipationRequestDto> getEventRequests(Long eventId) {
                throw unavailable();
            }

            @Override
            public EventRequestStatusUpdateResult updateEventRequests(
                    Long eventId,
                    EventRequestStatusUpdateRequest request
            ) {
                throw unavailable();
            }
        };
    }
}
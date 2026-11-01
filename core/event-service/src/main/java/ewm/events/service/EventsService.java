package ewm.events.service;

import ewm.events.dto.*;
import ewm.events.dto.params.AdminEventParams;
import ewm.events.dto.params.PaginationParams;
import ewm.events.dto.params.PublicEventParams;
import ewm.events.dto.params.UserEventPathParams;
import ewm.feign.client.dto.EventRequestStatusUpdateRequest;
import ewm.feign.client.dto.EventRequestStatusUpdateResult;
import ewm.feign.client.dto.ParticipationRequestDto;

import java.util.List;

public interface EventsService {

    List<EventShortDto> getEvents(PublicEventParams params);

    EventFullDto getEvent(Long id, Long userId);

    EventFullDto createEvent(Long userId, NewEventDto dto);

    List<EventShortDto> getUserEvents(Long userId, PaginationParams params);

    EventFullDto getUserEvent(UserEventPathParams params);

    EventFullDto updateUserEvent(UserEventPathParams params, UpdateEventUserRequest dto);

    List<EventFullDto> getAdminEvents(AdminEventParams params);

    EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest request);

    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult updateEventRequests(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest request);

    EventRequestInfoDto getRequestInfo(Long eventId);

    List<EventShortDto> getEventsByIds(List<Long> eventIds);

    boolean existsByCategoryId(Long categoryId);
}

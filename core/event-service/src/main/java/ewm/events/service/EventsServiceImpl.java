package ewm.events.service;

import client.StatsClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ewm.dto.EndpointHitDto;
import ewm.dto.ViewStatsDto;
import ewm.events.dto.*;
import ewm.events.dto.params.AdminEventParams;
import ewm.events.dto.params.PaginationParams;
import ewm.events.dto.params.PublicEventParams;
import ewm.events.dto.params.UserEventPathParams;
import ewm.events.mapper.EventMapper;
import ewm.events.model.Event;
import ewm.events.model.State;
import ewm.events.repository.EventsRepository;
import ewm.events.repository.specification.EventSpecification;
import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.exception.ValidationException;
import ewm.feign.client.CategoryClient;
import ewm.feign.client.ParticipationRequestClient;
import ewm.feign.client.UserClient;
import ewm.feign.client.dto.*;
import ewm.feign.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EventsServiceImpl implements EventsService {

    private final EventsRepository eventsRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;
    private final ObjectMapper objectMapper;
    private final UserClient userClient;
    private final UserMapper userMapper;
    private final ParticipationRequestClient participationRequestClient;
    private final CategoryClient categoryClient;

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
    private static final String APP_NAME = "ewm-main-service";

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getEvents(PublicEventParams params, HttpServletRequest request) {

        LocalDateTime startDate = params.getRangeStart();
        LocalDateTime endDate = params.getRangeEnd();

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ValidationException("rangeStart must be before rangeEnd");
        }

        sendHitToStats("/events", request.getRemoteAddr());

        Specification<Event> spec = Specification
                .where(EventSpecification.published())
                .and(EventSpecification.hasText(params.getText()))
                .and(EventSpecification.hasCategories(params.getCategories()))
                .and(EventSpecification.hasPaid(params.getPaid()))
                //.and(EventSpecification.onlyAvailable(params.getOnlyAvailable()))
                .and(EventSpecification.dateAfter(startDate))
                .and(EventSpecification.dateBefore(endDate));

        if (Boolean.TRUE.equals(params.getOnlyAvailable())) {
            return getAvailableEvents(spec, params);
        }

        return switch (params.getSort()) {
            case VIEWS -> getEventsSortedByViews(spec, params);
            case EVENT_DATE -> getEventsSortedByDate(spec, params);
            case null -> getEventsSortedByDate(spec, params);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> getAdminEvents(AdminEventParams params) {

        LocalDateTime startDate = params.getRangeStart();
        LocalDateTime endDate = params.getRangeEnd();

        PageRequest pageRequest = PageRequest.of(
                params.getFrom() / params.getSize(),
                params.getSize(),
                Sort.by(Sort.Direction.DESC, "eventDate")
        );

        List<State> states = params.getStates() == null
                ? null
                : params.getStates().stream()
                .map(State::valueOf)
                .toList();

        Specification<Event> spec = Specification
                .where(EventSpecification.hasUser(params.getUsers()))
                .and(EventSpecification.hasStates(states)
                        .and(EventSpecification.hasCategories(params.getCategories()))
                        .and(EventSpecification.dateAfter(startDate))
                        .and(EventSpecification.dateBefore(endDate)));

        Page<Event> page = eventsRepository.findAll(spec, pageRequest);

        List<Event> events = page.getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventFullDto> eventFullDtoList = eventMapper.toEventFullDtoList(page.getContent());
        enrichEventFullDtosWithInitiators(events, eventFullDtoList);
        enrichEventFullDtosWithStats(eventFullDtoList);

        return eventFullDtoList;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long id, HttpServletRequest request) {
        Event event = eventsRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event с id=" + id + " не найден"));

        if (event.getState() != State.PUBLISHED) {
            throw new NotFoundException("Event с id=" + id + " не найден");
        }

        sendHitToStats("/events/" + id, request.getRemoteAddr());

        EventFullDto dto = eventMapper.toEventFullDto(event);
        enrichEventFullDtoWithInitiator(event, dto);
        enrichEventWithStats(dto);

        return dto;
    }

    @Override
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        checkUserExists(userId);
        Long categoryId = dto.getCategory();
        checkCategoryExists(categoryId);

        LocalDateTime eventDate = parseAndValidateEventDate(
                dto.getEventDate(),
                "Дата события должна быть не ранее чем через 2 часа от текущего момента"
        );

        Event event = eventMapper.toEvent(dto, categoryId, userId);
        event.setCreatedOn(LocalDateTime.now());
        event.setState(State.PENDING);
        event.setEventDate(eventDate);

        event = eventsRepository.save(event);

        EventFullDto result = eventMapper.toEventFullDto(event);
        enrichEventFullDtoWithInitiator(event, result);
        result.setViews(0L);
        result.setConfirmedRequests(0L);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getUserEvent(UserEventPathParams params) {
        Event event = eventsRepository.findByIdAndInitiatorId(params.getEventId(), params.getUserId())
                .orElseThrow(() -> new NotFoundException("Event с id=" + params.getEventId() + " не найден"));

        EventFullDto dto = eventMapper.toEventFullDto(event);
        enrichEventFullDtoWithInitiator(event, dto);
        enrichEventWithStats(dto);

        return dto;
    }


    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getUserEvents(Long userId, PaginationParams params, HttpServletRequest request) {
        checkUserExists(userId);

        sendHitToStats("/users/" + userId + "/events", request.getRemoteAddr());

        PageRequest pageRequest = PageRequest.of(
                params.getFrom() / params.getSize(),
                params.getSize(),
                Sort.by(Sort.Direction.DESC, "eventDate"));
        Page<Event> page = eventsRepository.findByInitiatorId(userId, pageRequest);
        List<Event> events = page.getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventShortDto> dtos = eventMapper.toEventShortDtoList(events);
        enrichEventShortDtosWithInitiators(events, dtos);
        enrichEventsWithStats(dtos);

        return dtos;
    }

    @Override
    public EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest request) {
        Event event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event с id=" + eventId + " не найден"));

        updateAdminEventFields(event, request);
        handleAdminStateAction(event, request.getStateAction());

        event = eventsRepository.save(event);

        EventFullDto result = eventMapper.toEventFullDto(event);
        enrichEventFullDtoWithInitiator(event, result);
        enrichEventWithStats(result);

        return result;
    }

    @Override
    public EventFullDto updateUserEvent(UserEventPathParams params, UpdateEventUserRequest dto) {
        Event event = eventsRepository.findByIdAndInitiatorId(params.getEventId(), params.getUserId())
                .orElseThrow(() -> new NotFoundException("Event с id=" + params.getEventId() + " не найден"));

        if (event.getState() == State.PUBLISHED) {
            throw new ConflictException("Можно изменять только отмененные события или события в состоянии ожидания модерации");
        }

        if (dto.getEventDate() != null) {
            LocalDateTime eventDate = parseAndValidateEventDate(
                    dto.getEventDate(),
                    "Дата события должна быть не ранее чем через 2 часа от текущего момента"
            );
            event.setEventDate(eventDate);
        }

        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());
        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());

        if (dto.getCategory() != null) {
            //Category category = getCategoryOrThrow(dto.getCategory());
            Long categoryId = dto.getCategory();
            checkCategoryExists(categoryId);
            event.setCategoryId(categoryId);
        }

        if (dto.getLocation() != null) {
            event.getLocation().setLat(dto.getLocation().getLat());
            event.getLocation().setLon(dto.getLocation().getLon());
        }

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case StateAction.SEND_TO_REVIEW -> event.setState(State.PENDING);
                case StateAction.CANCEL_REVIEW -> event.setState(State.CANCELED);
                default -> throw new ValidationException("Неизвестное состояние: " + dto.getStateAction());
            }
        }

        event = eventsRepository.save(event);

        EventFullDto result = eventMapper.toEventFullDto(event);
        enrichEventFullDtoWithInitiator(event, result);
        enrichEventWithStats(result);

        return result;
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventsRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new ValidationException("Event с id=" + eventId + " не найден"));

        return participationRequestClient.getEventRequests(eventId);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateEventRequests(
            Long userId,
            Long eventId,
            EventRequestStatusUpdateRequest request
    ) {
        Event event = eventsRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() ->
                        new NotFoundException("Event с id=" + eventId + " не найден"));

        request.setParticipantLimit(event.getParticipantLimit());

        return participationRequestClient.updateEventRequests(eventId, request);
    }

//    @Override
//    @Transactional
//    public EventRequestStatusUpdateResult updateEventRequests(Long userId, Long eventId,
//                                                              EventRequestStatusUpdateRequest request) {
//        Event event = eventsRepository.findByIdAndInitiatorId(eventId, userId)
//                .orElseThrow(() -> new NotFoundException("Event с id=" + eventId + " не найден"));
//
//        List<Long> requestIds = request.getRequestIds();
//        if (requestIds == null || requestIds.isEmpty()) {
//            return EventRequestStatusUpdateResult.builder()
//                    .confirmedRequests(List.of())
//                    .rejectedRequests(List.of())
//                    .build();
//        }
//
//        List<ParticipationRequest> requests = participationRequestsRepository.findAllById(requestIds);
//
//        boolean hasNotPending = requests.stream()
//                .anyMatch(r -> r.getStatus() != ParticipationStatus.PENDING);
//        if (hasNotPending) {
//            throw new ConflictException("статус можно изменить только у заявок, находящихся в состоянии ожидания");
//        }
//
//        ParticipationStatus targetStatus = request.getStatus();
//        if (targetStatus != ParticipationStatus.CONFIRMED && targetStatus != ParticipationStatus.REJECTED) {
//            throw new ConflictException("доступные статусы только CONFIRMED/REJECTED");
//        }
//
//        if (targetStatus == ParticipationStatus.REJECTED) {
//            requests.forEach(r -> r.setStatus(ParticipationStatus.REJECTED));
//            List<ParticipationRequest> saved = participationRequestsRepository.saveAll(requests);
//
//            List<ParticipationRequestDto> toResponse = participationRequestMapper.toDtoList(saved);
//            return EventRequestStatusUpdateResult.builder()
//                    .confirmedRequests(List.of())
//                    .rejectedRequests(toResponse)
//                    .build();
//        }
//
//        Integer limit = event.getParticipantLimit();
//        if (limit != null && limit > 0) {
//            long alreadyConfirmed = participationRequestsRepository.countByEventIdAndStatus(eventId,
//                    ParticipationStatus.CONFIRMED);
//            long available = limit - alreadyConfirmed;
//
//            if (available <= 0) {
//                throw new ConflictException("достигнут лимит по заявкам на данное событие");
//            }
//
//            List<ParticipationRequest> toConfirm = new ArrayList<>();
//            List<ParticipationRequest> toReject = new ArrayList<>();
//
//            for (ParticipationRequest r : requests) {
//                if (available > 0) {
//                    r.setStatus(ParticipationStatus.CONFIRMED);
//                    toConfirm.add(r);
//                    available--;
//                } else {
//                    r.setStatus(ParticipationStatus.REJECTED);
//                    toReject.add(r);
//                }
//            }
//
//            participationRequestsRepository.saveAll(requests);
//
//            long confirmedNow = toConfirm.size();
//            long totalConfirmed = alreadyConfirmed + confirmedNow;
//            if (totalConfirmed >= limit) {
//                List<ParticipationRequest> pending = participationRequestsRepository.findAllByEventIdAndStatus(eventId,
//                        ParticipationStatus.PENDING);
//
//                pending.forEach(r -> r.setStatus(ParticipationStatus.REJECTED));
//                participationRequestsRepository.saveAll(pending);
//            }
//
//            return EventRequestStatusUpdateResult.builder()
//                    .confirmedRequests(participationRequestMapper.toDtoList(toConfirm))
//                    .rejectedRequests(participationRequestMapper.toDtoList(toReject))
//                    .build();
//        }
//
//
//        requests.forEach(r -> r.setStatus(ParticipationStatus.CONFIRMED));
//        List<ParticipationRequest> saved = participationRequestsRepository.saveAll(requests);
//
//        return EventRequestStatusUpdateResult.builder()
//                .confirmedRequests(participationRequestMapper.toDtoList(saved))
//                .rejectedRequests(List.of())
//                .build();
//    }

    private void sendHitToStats(String uri, String ip) {
        statsClient.saveHit(EndpointHitDto.builder()
                .app(APP_NAME)
                .uri(uri)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private Map<Long, Long> getViewsMap(List<Long> eventIds) {

        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> uris = eventIds.stream()
                .map(id -> "/events/" + id)
                .toList();

        ResponseEntity<Object> response = statsClient.getStats(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.now(),
                uris,
                true
        );

        if (response.getBody() == null) {
            return Collections.emptyMap();
        }

        List<ViewStatsDto> stats = objectMapper.convertValue(
                response.getBody(),
                new TypeReference<List<ViewStatsDto>>() {}
        );

        if (stats == null || stats.isEmpty()) {
            return Collections.emptyMap();
        }

        return stats.stream()
                .collect(Collectors.toMap(
                        stat -> Long.parseLong(
                                stat.getUri().substring(
                                        stat.getUri().lastIndexOf('/') + 1
                                )
                        ),
                        ViewStatsDto::getHits,
                        (existing, replacement) -> existing
                ));
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return participationRequestClient.getConfirmedRequests(eventIds)
                .stream()
                        .collect(Collectors.toMap(
                                EventRequestsCountDto::eventId,
                                EventRequestsCountDto::confirmedRequests
                        ));
    }

    private void enrichEventWithStats(EventFullDto dto) {
        Map<Long, Long> viewsMap = getViewsMap(List.of(dto.getId()));
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(List.of(dto.getId()));

        dto.setViews(viewsMap.getOrDefault(dto.getId(), 0L));
        dto.setConfirmedRequests(confirmedMap.getOrDefault(dto.getId(), 0L));
    }

    private void enrichEventsWithStats(List<EventShortDto> dtos) {
        if (dtos.isEmpty()) {
            return;
        }

        List<Long> eventIds = dtos.stream()
                .map(EventShortDto::getId)
                .toList();

        Map<Long, Long> viewsMap = getViewsMap(eventIds);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(eventIds);

        dtos.forEach(dto -> {
            dto.setViews(viewsMap.getOrDefault(dto.getId(), 0L));
            dto.setConfirmedRequests(confirmedMap.getOrDefault(dto.getId(), 0L));
        });
    }

    private void enrichEventFullDtosWithStats(List<EventFullDto> dtos) {
        if (dtos.isEmpty()) {
            return;
        }

        List<Long> eventIds = dtos.stream()
                .map(EventFullDto::getId)
                .toList();

        Map<Long, Long> viewsMap = getViewsMap(eventIds);
        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(eventIds);

        dtos.forEach(dto -> {
            dto.setViews(viewsMap.getOrDefault(dto.getId(), 0L));
            dto.setConfirmedRequests(confirmedMap.getOrDefault(dto.getId(), 0L));
        });
    }

    private LocalDateTime parseAndValidateEventDate(String dateStr, String errorMessage) {
        LocalDateTime eventDate;
        try {
            eventDate = LocalDateTime.parse(dateStr, FORMATTER);
        } catch (Exception e) {
            throw new ValidationException("Неверный формат даты. Ожидается формат: " + DATE_FORMAT);
        }

        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException(errorMessage);
        }

        return eventDate;
    }

//    private Category getCategoryOrThrow(Long categoryId) {
//        return categoryRepository.findById(categoryId)
//                .orElseThrow(() -> new NotFoundException("Category с id=" + categoryId + " не найдена"));
//    }

    private void updateAdminEventFields(Event event, UpdateEventAdminRequest request) {
        if (request.getEventDate() != null) {
            LocalDateTime eventDate;
            try {
                eventDate = LocalDateTime.parse(request.getEventDate(), FORMATTER);
            } catch (Exception e) {
                throw new ValidationException("Неверный формат даты. Ожидается формат: " + DATE_FORMAT);
            }

            if (event.getState() == State.PUBLISHED && eventDate.isBefore(event.getPublishedOn().plusHours(1))) {
                throw new ConflictException("Дата начала изменяемого события должна быть не ранее чем за час от даты публикации");
            }

            if (eventDate.isBefore(LocalDateTime.now())) {
                throw new ValidationException("Дата события не может быть в прошлом");
            }

            if (event.getState() == State.PUBLISHED && eventDate.isBefore(event.getPublishedOn().plusHours(1))) {
                throw new ConflictException("Дата начала изменяемого события должна быть не ранее чем за час от даты публикации");
            }

            event.setEventDate(eventDate);
        }

        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());

        if (request.getCategory() != null) {
            Long categoryId = request.getCategory();
            checkCategoryExists(categoryId);
            event.setCategoryId(categoryId);
        }

        if (request.getLocation() != null) {
            event.getLocation().setLat(request.getLocation().getLat());
            event.getLocation().setLon(request.getLocation().getLon());
        }
    }

    private void handleAdminStateAction(Event event, StateAction stateAction) {
        if (stateAction == null) {
            return;
        }

        switch (stateAction) {
            case StateAction.PUBLISH_EVENT -> {
                if (event.getState() != State.PENDING) {
                    throw new ConflictException("Событие должно находиться в состоянии ожидания публикации");
                }
                event.setState(State.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            }
            case StateAction.REJECT_EVENT -> {
                if (event.getState() == State.PUBLISHED) {
                    throw new ConflictException("Нельзя отклонить опубликованное событие");
                }
                event.setState(State.CANCELED);
            }
            default -> throw new ValidationException("Неизвестное состояние: " + stateAction);
        }
    }

    private List<EventShortDto> getEventsSortedByDate(Specification<Event> spec, PublicEventParams params) {
        PageRequest pageRequest = PageRequest.of(
                params.getFrom() / params.getSize(),
                params.getSize(),
                Sort.by(Sort.Direction.ASC, "eventDate")
        );

        Page<Event> page = eventsRepository.findAll(spec, pageRequest);
        List<Event> events = page.getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventShortDto> dtos = eventMapper.toEventShortDtoList(events);
        enrichEventShortDtosWithInitiators(events, dtos);
        enrichEventsWithStats(dtos);

        return dtos;
    }

    private List<EventShortDto> getEventsSortedByViews(Specification<Event> spec, PublicEventParams params) {
        List<Event> allEvents = eventsRepository.findAll(spec);

        if (allEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventShortDto> dtos = eventMapper.toEventShortDtoList(allEvents);
        enrichEventShortDtosWithInitiators(allEvents, dtos);
        enrichEventsWithStats(dtos);

        dtos.sort(Comparator.comparing(EventShortDto::getViews).reversed());

        int from = params.getFrom();
        int size = params.getSize();

        if (from >= dtos.size()) {
            return Collections.emptyList();
        }

        int toIndex = Math.min(from + size, dtos.size());
        return dtos.subList(from, toIndex);
    }

    private void checkUserExists(Long userId) {
        List<UserResponseDto> users = userClient.getUsersByIds(List.of(userId));

        if (users.isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
    }

    private Map<Long, UserShortDto> getInitiatorsByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userClient.getUsersByIds(
                        userIds.stream()
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(
                        UserResponseDto::getId,
                        userMapper::toUserShortDto
                ));
    }

    private void enrichEventFullDtoWithInitiator(Event event, EventFullDto dto) {
        Map<Long, UserShortDto> initiators =
                getInitiatorsByIds(List.of(event.getInitiatorId()));

        dto.setInitiator(getInitiatorOrThrow(
                initiators,
                event.getInitiatorId()
        ));
    }

    private void enrichEventFullDtosWithInitiators(
            List<Event> events,
            List<EventFullDto> dtos
    ) {
        Map<Long, UserShortDto> initiators = getInitiatorsByIds(
                events.stream()
                        .map(Event::getInitiatorId)
                        .distinct()
                        .toList()
        );

        Map<Long, Long> initiatorIdsByEventId = events.stream()
                .collect(Collectors.toMap(
                        Event::getId,
                        Event::getInitiatorId
                ));

        dtos.forEach(dto -> dto.setInitiator(
                getInitiatorOrThrow(
                        initiators,
                        initiatorIdsByEventId.get(dto.getId())
                )
        ));
    }

    private void enrichEventShortDtosWithInitiators(
            List<Event> events,
            List<EventShortDto> dtos
    ) {
        Map<Long, UserShortDto> initiators = getInitiatorsByIds(
                events.stream()
                        .map(Event::getInitiatorId)
                        .distinct()
                        .toList()
        );

        Map<Long, Long> initiatorIdsByEventId = events.stream()
                .collect(Collectors.toMap(
                        Event::getId,
                        Event::getInitiatorId
                ));

        dtos.forEach(dto -> dto.setInitiator(
                getInitiatorOrThrow(
                        initiators,
                        initiatorIdsByEventId.get(dto.getId())
                )
        ));
    }

    private UserShortDto getInitiatorOrThrow(
            Map<Long, UserShortDto> initiators,
            Long initiatorId
    ) {
        UserShortDto initiator = initiators.get(initiatorId);

        if (initiator == null) {
            throw new NotFoundException(
                    "Инициатор с id=" + initiatorId + " не найден в user-service"
            );
        }

        return initiator;
    }

    @Override
    @Transactional(readOnly = true)
    public EventRequestInfoDto getRequestInfo(Long eventId) {
        Event event = eventsRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Событие не найдено: event_id = " + eventId));

        return EventRequestInfoDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiatorId())
                .state(event.getState().name())
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.getRequestModeration())
                .build();
    }

    private void checkCategoryExists(Long categoryId) {
        if (categoryId == null) {
            throw new ValidationException("Идентификатор категории не должен быть null");
        }

        categoryClient.getCategory(categoryId);
    }

    private List<EventShortDto> getAvailableEvents(
            Specification<Event> spec,
            PublicEventParams params
    ) {
        List<Event> events = eventsRepository.findAll(spec);

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        Map<Long, Long> confirmedRequests = getConfirmedRequestsMap(eventIds);

        List<Event> availableEvents = events.stream()
                .filter(event -> {
                    int participantLimit = event.getParticipantLimit();

                    return participantLimit == 0
                            || confirmedRequests.getOrDefault(event.getId(), 0L)
                            < participantLimit;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        if (availableEvents.isEmpty()) {
            return Collections.emptyList();
        }

        // Для сортировки по дате сортируем сущности до преобразования в DTO.
        if (params.getSort() != EventSort.VIEWS) {
            availableEvents.sort(Comparator.comparing(Event::getEventDate));
        }

        List<EventShortDto> dtos =
                eventMapper.toEventShortDtoList(availableEvents);

        enrichEventShortDtosWithInitiators(availableEvents, dtos);
        enrichEventsWithStats(dtos);

        // Сортировка по просмотрам: по убыванию,
        // при равенстве — по ID по возрастанию.
        if (params.getSort() == EventSort.VIEWS) {
            dtos.sort(
                    Comparator.comparing(
                            EventShortDto::getViews,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ).thenComparing(EventShortDto::getId)
            );
        }

        // Проверяем параметры пагинации.
        int from = params.getFrom() == null ? 0 : params.getFrom();
        int size = params.getSize() == null ? 10 : params.getSize();

        if (from >= dtos.size() || size == 0) {
            return Collections.emptyList();
        }

        int toIndex = (int) Math.min((long) from + size, dtos.size());

        return dtos.subList(from, toIndex);
    }
}
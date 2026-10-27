package ewm.participationRequest.service;

import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.exception.ValidationException;
import ewm.feign.client.EventClient;
import ewm.feign.client.UserClient;
import ewm.feign.client.dto.EventRequestInfoDto;
import ewm.feign.client.dto.UserResponseDto;
import ewm.participationRequest.dto.EventRequestStatusUpdateRequest;
import ewm.participationRequest.dto.EventRequestStatusUpdateResult;
import ewm.participationRequest.dto.EventRequestsCountDto;
import ewm.participationRequest.dto.ParticipationRequestDto;
import ewm.participationRequest.mapper.ParticipationRequestMapper;
import ewm.participationRequest.model.ParticipationRequest;
import ewm.participationRequest.model.ParticipationStatus;
import ewm.participationRequest.repository.ParticipationRequestsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ParticipationRequestServiceImpl implements ParticipationRequestService {
    private static final String USER_NOT_FOUND = "Пользователь не найден: user_id = ";
    private static final String EVENT_NOT_FOUND = "Событие не найдено: event_id = ";
    private static final String REQUEST_NOT_FOUND = "Заявка не найдена: request_id = ";
    private static final String REQUEST_ALREADY_EXISTS = "Запрос уже существует";
    private static final String REQUEST_YOUR_EVENT = "Инициатор события не может добавить запрос на участие в своём событии";
    private static final String EVENT_NOT_PUBLISHED = "Событие еще не опубликовано";
    private static final String EVENT_OVER_LIMIT = "Достигнут лимит участников на данное событие";
    private static final String CANCEL_YOUR_REQUEST = "Можно отменить только свою заявку";

    private final UserClient userClient;
    private final EventClient eventClient;
    private final ParticipationRequestsRepository requestRepository;
    private final ParticipationRequestMapper requestMapper;

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        List<UserResponseDto> users = userClient.getUsersByIds(List.of(userId));

        if (users.isEmpty()) {
            throw new ValidationException(USER_NOT_FOUND + userId);
        }

        List<ParticipationRequest> requests = requestRepository.findAllByRequesterId(userId);

        return requests.stream()
            .map(requestMapper::toDto)
            .toList();
    }

    @Override
    @Transactional
    public ParticipationRequestDto addParticipationRequest(Long userId, Long eventId) {
        checkUserExists(userId);

        EventRequestInfoDto event = eventClient.getRequestInfo(eventId);

        ParticipationRequest request = new ParticipationRequest();
        Long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, ParticipationStatus.CONFIRMED);

        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException(REQUEST_ALREADY_EXISTS);
        }

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException(REQUEST_YOUR_EVENT);
        }

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException(EVENT_NOT_PUBLISHED);
        }

        if (event.getParticipantLimit() != 0 && event.getParticipantLimit() <= confirmedRequests) {
            throw new ConflictException(EVENT_OVER_LIMIT);
        }

        if (event.getParticipantLimit() == 0 || !event.getRequestModeration()) {
            request.setStatus(ParticipationStatus.CONFIRMED);
        } else {
            request.setStatus(ParticipationStatus.PENDING);
        }

        request.setRequesterId(userId);
        request.setEventId(eventId);
        request.setCreated(LocalDateTime.now());

        ParticipationRequest saveRequest = requestRepository.save(request);

        return requestMapper.toDto(saveRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        checkUserExists(userId);

        ParticipationRequest request = requestRepository.findById(requestId).orElseThrow(
            () -> new NotFoundException(REQUEST_NOT_FOUND + requestId)
        );

        if (!request.getRequesterId().equals(userId)) {
            throw new NotFoundException(CANCEL_YOUR_REQUEST);
        }

        request.setStatus(ParticipationStatus.CANCELED);
        requestRepository.save(request);

        return requestMapper.toDto(request);
    }

    private void checkUserExists(Long userId) {
        List<UserResponseDto> users = userClient.getUsersByIds(List.of(userId));

        if (users.isEmpty()) {
            throw new NotFoundException(USER_NOT_FOUND + userId);
        }
    }

    @Override
    public List<EventRequestsCountDto> getConfirmedRequests(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            log.info("getConfirmedRequests: получен пустой список eventIds");
            return List.of();
        }

        return requestRepository.getConfirmedRequests(eventIds);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long eventId) {
        return requestRepository.findAllByEventId(eventId)
                .stream()
                .map(requestMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateEventRequests(
            Long eventId,
            EventRequestStatusUpdateRequest request
    ) {
        List<Long> requestIds = request.getRequestIds();

        if (requestIds == null || requestIds.isEmpty()) {
            return EventRequestStatusUpdateResult.builder()
                    .confirmedRequests(List.of())
                    .rejectedRequests(List.of())
                    .build();
        }

        ParticipationStatus targetStatus = request.getStatus();

        if (targetStatus != ParticipationStatus.CONFIRMED
                && targetStatus != ParticipationStatus.REJECTED) {
            throw new ConflictException(
                    "Доступные статусы только CONFIRMED/REJECTED"
            );
        }

        List<ParticipationRequest> requests =
                requestRepository.findAllById(requestIds);

        // Каждая заявка должна существовать и относиться к этому событию.
        if (requests.size() != requestIds.stream().distinct().count()
                || requests.stream().anyMatch(
                r -> !r.getEventId().equals(eventId))) {
            throw new NotFoundException(
                    "Одна или несколько заявок не найдены для события: " + eventId
            );
        }

        if (requests.stream().anyMatch(
                r -> r.getStatus() != ParticipationStatus.PENDING)) {
            throw new ConflictException(
                    "Статус можно изменить только у заявок, находящихся в состоянии ожидания"
            );
        }

        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();

        if (targetStatus == ParticipationStatus.REJECTED) {
            requests.forEach(r -> {
                r.setStatus(ParticipationStatus.REJECTED);
                rejected.add(r);
            });

            requestRepository.saveAll(rejected);
        } else {
            Integer limit = request.getParticipantLimit();

            if (limit == null || limit == 0) {
                requests.forEach(r -> {
                    r.setStatus(ParticipationStatus.CONFIRMED);
                    confirmed.add(r);
                });
            } else {
                long alreadyConfirmed = requestRepository.countByEventIdAndStatus(
                        eventId,
                        ParticipationStatus.CONFIRMED
                );

                long available = limit - alreadyConfirmed;

                if (available <= 0) {
                    throw new ConflictException(
                            "Достигнут лимит по заявкам на данное событие"
                    );
                }

                for (ParticipationRequest participationRequest : requests) {
                    if (available > 0) {
                        participationRequest.setStatus(
                                ParticipationStatus.CONFIRMED
                        );
                        confirmed.add(participationRequest);
                        available--;
                    } else {
                        participationRequest.setStatus(
                                ParticipationStatus.REJECTED
                        );
                        rejected.add(participationRequest);
                    }
                }
            }

            requestRepository.saveAll(requests);

            // Если лимит достигнут, отклоняем оставшиеся ожидающие заявки.
            if (limit != null && limit > 0) {
                long totalConfirmed = requestRepository.countByEventIdAndStatus(
                        eventId,
                        ParticipationStatus.CONFIRMED
                );

                if (totalConfirmed >= limit) {
                    List<ParticipationRequest> pending =
                            requestRepository.findAllByEventIdAndStatus(
                                    eventId,
                                    ParticipationStatus.PENDING
                            );

                    pending.forEach(r ->
                            r.setStatus(ParticipationStatus.REJECTED));

                    requestRepository.saveAll(pending);
                }
            }
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed.stream()
                        .map(requestMapper::toDto)
                        .toList())
                .rejectedRequests(rejected.stream()
                        .map(requestMapper::toDto)
                        .toList())
                .build();
    }
}

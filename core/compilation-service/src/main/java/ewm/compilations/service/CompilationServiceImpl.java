package ewm.compilations.service;

import ewm.compilations.dto.NewCompilationDto;
import ewm.compilations.dto.UpdateCompilationRequest;
import ewm.compilations.mapper.CompilationMapper;
import ewm.compilations.model.Compilation;
import ewm.compilations.repository.CompilationRepository;
import ewm.exception.NotFoundException;
import ewm.feign.client.EventClient;
import ewm.feign.client.dto.EventRequestInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ewm.compilations.dto.CompilationDto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventClient eventClient;
    private final CompilationMapper compilationMapper;

    @Override
    public CompilationDto save(NewCompilationDto newCompilationDto) {
        log.info("save new compilation {}", newCompilationDto);
        Compilation compilation = compilationMapper.toCompilationfromNewDto(newCompilationDto);

        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            compilation.setEventIds(
                    loadEventIds(newCompilationDto.getEvents())
            );
        }
        log.info("saves compilation {}", compilation);
        return toCompilationDto(compilationRepository.save(compilation));
    }

    @Override
    public CompilationDto update(Long compId, UpdateCompilationRequest updateCompilationRequest) {
        log.info("update compilation request {}", updateCompilationRequest);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Compilation with id=" + compId + " was not found"));

        if (updateCompilationRequest.getTitle() != null) {
            compilation.setTitle(updateCompilationRequest.getTitle());
        }

        if (updateCompilationRequest.getPinned() != null) {
            compilation.setPinned(updateCompilationRequest.getPinned());
        }

        if (updateCompilationRequest.getEvents() != null) {
            compilation.setEventIds(
                    loadEventIds(updateCompilationRequest.getEvents())
            );
        }
        log.info("updates compilation {}", compilation);
        return toCompilationDto(compilationRepository.save(compilation));
    }

    @Override
    public void delete(Long compId) {
        log.info("delete compilation {}", compId);
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException(
                    "Compilation with id=" + compId + " was not found");
        }

        compilationRepository.deleteById(compId);
        log.info("deletes compilation {}", compId);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        log.info("get compilations pinned {} from {} size {}", pinned, from, size);
        Pageable pageable = PageRequest.of(from / size, size);

        List<Compilation> compilations = compilationRepository.findAllByPinned(pinned, pageable);
        log.info("get compilations {}", compilations.size());
        return compilations.stream()
                .map(this::toCompilationDto)
                .toList();
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        log.info("get compilation by id {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(
                        "Compilation with id=" + compId + " was not found"
                ));
        log.info("get compilation by id {}", compilation);
        return toCompilationDto(compilation);
    }

    private Set<Long> loadEventIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> uniqueIds = new HashSet<>(ids);

        List<EventRequestInfoDto> events = eventClient.getEventsByIds(
                List.copyOf(uniqueIds)
        );

        validateEventsFound(events, uniqueIds);

        return uniqueIds;
    }

    private CompilationDto toCompilationDto(Compilation compilation) {
        CompilationDto dto = compilationMapper.toCompilationDto(compilation);
        Set<Long> eventIds = compilation.getEventIds();

        if (eventIds == null || eventIds.isEmpty()) {
            dto.setEvents(List.of());
            return dto;
        }

        List<EventRequestInfoDto> events =
                eventClient.getEventsByIds(List.copyOf(eventIds));

        validateEventsFound(events, eventIds);

        dto.setEvents(events);
        return dto;
    }

    private void validateEventsFound(
            List<EventRequestInfoDto> events,
            Set<Long> expectedIds
    ) {
        Set<Long> foundIds = events.stream()
                .map(EventRequestInfoDto::getId)
                .collect(Collectors.toSet());

        if (!foundIds.containsAll(expectedIds)) {
            throw new NotFoundException("Не все события были найдены");
        }
    }
}

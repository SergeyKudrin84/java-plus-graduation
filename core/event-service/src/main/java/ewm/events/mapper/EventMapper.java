package ewm.events.mapper;

import ewm.events.dto.EventFullDto;
import ewm.events.dto.EventShortDto;
import ewm.events.dto.LocationDto;
import ewm.events.dto.NewEventDto;
import ewm.events.model.Event;
import ewm.events.model.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdOn", ignore = true)
    @Mapping(target = "publishedOn", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "eventDate", ignore = true)
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "initiatorId", source = "initiatorId")
    @Mapping(target = "location", source = "dto.location")
    Event toEvent(NewEventDto dto, Long categoryId, Long initiatorId);

    @Mapping(target = "views", ignore = true)
    @Mapping(target = "confirmedRequests", ignore = true)
    EventFullDto toEventFullDto(Event event);

    @Mapping(target = "views", ignore = true)
    @Mapping(target = "confirmedRequests", ignore = true)
    EventShortDto toEventShortDto(Event event);

    @Mapping(target = "initiator", ignore = true)
    List<EventFullDto> toEventFullDtoList(List<Event> events);
    @Mapping(target = "initiator", ignore = true)
    List<EventShortDto> toEventShortDtoList(List<Event> events);

    LocationDto toLocationDto(Location location);

    @Mapping(target = "id", ignore = true)
    Location toLocation(LocationDto dto);
}
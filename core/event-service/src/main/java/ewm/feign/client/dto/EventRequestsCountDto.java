package ewm.feign.client.dto;

public record EventRequestsCountDto(
        Long eventId,
        Long confirmedRequests
) {
}

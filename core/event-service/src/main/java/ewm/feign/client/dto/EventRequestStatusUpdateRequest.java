package ewm.feign.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class EventRequestStatusUpdateRequest {
    List<Long> requestIds;
    ParticipationStatus status;
    private Integer participantLimit;
}

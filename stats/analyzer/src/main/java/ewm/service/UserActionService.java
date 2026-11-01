package ewm.service;

import ewm.model.Interaction;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.List;

public interface UserActionService {

    void process(UserActionAvro action);

    List<Interaction> getRecentInteractions(Long userId, int count);

    List<Interaction> getInteractions(Long userId, List<Long> eventIds);

}

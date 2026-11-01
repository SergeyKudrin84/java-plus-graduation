package ewm.service;

import ru.practicum.ewm.stats.avro.UserActionAvro;

public interface UserActionHandler {

    void handle(UserActionAvro userAction);
}
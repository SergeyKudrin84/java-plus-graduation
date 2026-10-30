package ru.practicum.ewm.collector.service;

import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;

@Component
public class UserActionMapper {
    public UserActionAvro toAvro(UserActionProto action) {
        return UserActionAvro.newBuilder()
                .setUserId(action.getUserId())
                .setEventId(action.getEventId())
                .setActionType(toAvroType(action.getActionType()))
                .setTimestamp(toEpochMillis(action.getTimestamp()))
                .build();
    }

    private ActionTypeAvro toAvroType(ActionTypeProto actionType) {
        return switch (actionType) {
            case ACTION_VIEW -> ActionTypeAvro.VIEW;
            case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> ActionTypeAvro.LIKE;
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unsupported action type: " + actionType);
        };
    }

    private long toEpochMillis(Timestamp timestamp) {
        return timestamp.getSeconds() * 1_000L + timestamp.getNanos() / 1_000_000L;
    }
}

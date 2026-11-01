package client;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

@Slf4j
@Component
public class CollectorClient {

    private final UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public CollectorClient(
            @GrpcClient("collector")
            UserActionControllerGrpc.UserActionControllerBlockingStub client
    ) {
        this.client = client;
    }

    public void collectUserAction(UserActionProto request) {
        client.collectUserAction(request);

        log.info("User action successfully sent: userId={}, eventId={}",
                request.getUserId(),
                request.getEventId());
    }
}
package ru.practicum.ewm.collector.grpc;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.ewm.collector.service.UserActionService;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

@GrpcService
public class UserActionGrpcService extends UserActionControllerGrpc.UserActionControllerImplBase {
    private final UserActionService userActionService;

    public UserActionGrpcService(UserActionService userActionService) {
        this.userActionService = userActionService;
    }

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        try {
            userActionService.collect(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (RuntimeException exception) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to collect user action")
                    .withCause(exception)
                    .asRuntimeException());
        }
    }
}

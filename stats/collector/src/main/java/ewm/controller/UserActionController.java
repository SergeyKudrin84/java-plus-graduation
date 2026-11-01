package ewm.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import ewm.kafka.KafkaClient;
import ewm.mapper.UserActionMapper;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

@GrpcService
@Slf4j
public class UserActionController
        extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final KafkaClient kafkaClient;
    private final UserActionMapper userActionMapper;

    public UserActionController(
            KafkaClient kafkaClient,
            UserActionMapper userActionMapper
    ) {
        this.kafkaClient = kafkaClient;
        this.userActionMapper = userActionMapper;
    }

    @Override
    public void collectUserAction(
            UserActionProto request,
            StreamObserver<Empty> responseObserver
    ) {
        try {
            log.info("userAction received: {}", request);
            UserActionAvro userAction = userActionMapper.toAvro(request);

            ProducerRecord<Long, SpecificRecordBase> record =
                    new ProducerRecord<>(
                            KafkaTopics.USER_ACTIONS,
                            userAction.getUserId(),
                            userAction
                    );

            kafkaClient.getProducer().send(record, (metadata, exception) -> {
                if (exception != null) {
                    responseObserver.onError(
                            Status.UNAVAILABLE
                                    .withDescription("Failed to send user action to Kafka")
                                    .withCause(exception)
                                    .asRuntimeException()
                    );
                    return;
                }

                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            });

        } catch (IllegalArgumentException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .withCause(exception)
                            .asRuntimeException()
            );
        } catch (RuntimeException exception) {
            log.error("Failed to initialize or send message to Kafka", exception);
            responseObserver.onError(
                    Status.UNAVAILABLE
                            .withDescription("Kafka producer is unavailable")
                            .withCause(exception)
                            .asRuntimeException()
            );
        }
    }
}

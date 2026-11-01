package serialization;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class GeneralAvroSerializer implements Serializer<SpecificRecordBase> {

    private final EncoderFactory encoderFactory = EncoderFactory.get();

    @Override
    public byte[] serialize(String topic, SpecificRecordBase data) {
        if (data == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            DatumWriter<SpecificRecordBase> writer =
                    new SpecificDatumWriter<>(data.getSchema());
//            Kafka Producer может вызывать сериализатор из нескольких потоков.
//            Общее изменяемое поле encoder создаёт риск одновременного использования одного объекта.
//            Лучше создавать Encoder локально при каждой сериализации
            BinaryEncoder encoder = encoderFactory.binaryEncoder(outputStream, null);

            writer.write(data, encoder);
            encoder.flush();

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new SerializationException(
                    "Ошибка сериализации данных для топика [" + topic + "]", e
            );
        }
    }
}

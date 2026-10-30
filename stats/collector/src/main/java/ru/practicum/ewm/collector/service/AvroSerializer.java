package ru.practicum.ewm.collector.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Component
public class AvroSerializer {
    public byte[] serialize(UserActionAvro action) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
            new SpecificDatumWriter<>(UserActionAvro.class).write(action, encoder);
            encoder.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize user action", exception);
        }
    }
}

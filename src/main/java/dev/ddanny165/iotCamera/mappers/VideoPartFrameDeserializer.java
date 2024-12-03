package dev.ddanny165.iotCamera.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.ddanny165.iotCamera.models.VideoPartFrame;

public final class VideoPartFrameDeserializer {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

    public static VideoPartFrame fromJson(String json) throws Exception {
        return objectMapper.readValue(json, VideoPartFrame.class);
    }
}


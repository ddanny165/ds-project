package dev.ddanny165.iotCamera.models;

import java.time.LocalDateTime;
import java.util.Optional;

public record VideoPartFrame(Integer nextVideoPart, Integer nextFrameToUse, Optional<LocalDateTime> accessedAt) {
}

package dev.ddanny165.iotCamera.rest.controllers;

import dev.ddanny165.iotCamera.exceptions.DynamoDbServiceException;
import dev.ddanny165.iotCamera.services.CameraService;
import dev.ddanny165.iotCamera.services.VideoPartFrameDynamoDBService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/camera")
public class CameraController {
    private final CameraService cameraService;

    public CameraController(CameraService cameraService, VideoPartFrameDynamoDBService videoPartFrameDynamoDBService, S3Client s3Client) {
        this.cameraService = cameraService;
    }

    @GetMapping("{id}/frame")
    public ResponseEntity<byte[]> getVideoFrame(@PathVariable("id") int cameraId) {
        if (cameraId < 1 || cameraId > this.cameraService.getNumberOfCameras()) {
            throw new IllegalArgumentException("Camera ID is out of bounds");
        }

        try {
            LocalDateTime accessedAt = LocalDateTime.now();
            return ResponseEntity.ok(cameraService.getVideoFrame(cameraId, accessedAt));
        } catch (IOException | DynamoDbServiceException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}


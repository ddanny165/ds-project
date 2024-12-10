package dev.ddanny165.iotCamera.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ddanny165.iotCamera.exceptions.DynamoDbServiceException;
import dev.ddanny165.iotCamera.models.VideoPartFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class CameraService {

    private final Integer numberOfCameras;
    private final VideoPartFrameDynamoDBService videoPartFrameDynamoDBService;
    private final Cache<String, File> videoCache;


    public CameraService(VideoPartFrameDynamoDBService videoPartFrameDynamoDBService) {
        this.videoPartFrameDynamoDBService = videoPartFrameDynamoDBService;
        this.numberOfCameras = 5;

        this.videoCache = Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    public Integer getNumberOfCameras() {
        return numberOfCameras;
    }

    public byte[] getVideoFrame(int cameraId, LocalDateTime accessedAt) throws IOException, DynamoDbServiceException {
        Optional<VideoPartFrame> videoPartFrameOpt = this.videoPartFrameDynamoDBService.getFrame(cameraId);
        if (videoPartFrameOpt.isEmpty()) {
            System.err.println("ERROR UPON GETTING THE CURRENT FRAME!");
        }
        VideoPartFrame videoPartFrame = videoPartFrameOpt.get();

        Integer nextVideoFrameToUse = videoPartFrame.nextFrameToUse();
        Integer nextVideoPartToUse = videoPartFrame.nextVideoPart();

        String videoFilePath = buildVideoKeyString(nextVideoPartToUse, cameraId);
        File videoFile = videoCache.get(videoFilePath, key -> new File(videoFilePath));

        if (!videoFile.exists()) {
            System.err.println("ERROR: Video file not found at path: " + videoFilePath);
        }

        boolean isVideoPartChanged = false;
        if (videoPartFrame.accessedAt().isPresent()) {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();

                int secondsPassed = (int) Duration.between(videoPartFrame.accessedAt().get(), accessedAt).getSeconds();
                int framesPerSecond = (int)grabber.getFrameRate();
                int frameOffset = secondsPassed * framesPerSecond;

                nextVideoFrameToUse += frameOffset;
                if (nextVideoFrameToUse >= grabber.getLengthInFrames()) {
                    // then we start with the next part of the video from the camera
                    nextVideoFrameToUse = 0;
                    if (nextVideoPartToUse == 11) {
                        nextVideoPartToUse = 1;
                    } else {
                        nextVideoPartToUse++;
                    }

                    isVideoPartChanged = true;
                }
                videoPartFrameDynamoDBService.saveFrame(cameraId, new VideoPartFrame(nextVideoPartToUse, nextVideoFrameToUse, Optional.of(accessedAt)));
            }
        }

        if (isVideoPartChanged) {
            String newVideoFilePath = buildVideoKeyString(nextVideoPartToUse, cameraId);
            videoFile = videoCache.get(videoFilePath, key -> new File(newVideoFilePath));
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();
            grabber.setFrameNumber(nextVideoFrameToUse);
            Frame frame = grabber.grabImage();

            BufferedImage bufferedImage = new Java2DFrameConverter().convert(frame);
            videoPartFrameDynamoDBService.saveFrame(cameraId, new VideoPartFrame(nextVideoPartToUse, ++nextVideoFrameToUse, Optional.of(accessedAt)));
            return convertImageToBytes(bufferedImage);
        }
    }

    private String buildVideoKeyString(Integer nextVideoPartToUse, Integer cameraId) {
        return "/app/videos" +
                "/output_video" +
                nextVideoPartToUse +
                "_" +
                cameraId +
                ".mp4";
    }

    private byte[] convertImageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}

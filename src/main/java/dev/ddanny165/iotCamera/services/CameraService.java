package dev.ddanny165.iotCamera.services;

import com.amazonaws.services.dynamodbv2.xspec.S;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ddanny165.iotCamera.exceptions.DynamoDbServiceException;
import dev.ddanny165.iotCamera.models.VideoPartFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class CameraService {
    private static final String BUCKET_NAME = "camera-videos-bucket-ds";
    // Folder in S3 bucket
    private static final String VIDEO_FOLDER = "edited-videos/output";
    private final Cache<String, File> videoCache;

    private final Integer numberOfCameras;
    private final S3Client s3Client;
    private final VideoPartFrameDynamoDBService videoPartFrameDynamoDBService;

    public CameraService(VideoPartFrameDynamoDBService videoPartFrameDynamoDBService, S3Client s3Client) {
        this.videoCache = Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();

        this.s3Client = s3Client;

        this.videoPartFrameDynamoDBService = videoPartFrameDynamoDBService;
        this.numberOfCameras = 5;

        // initialization code
//        for (int i = 0; i < numberOfCameras; i++) {
//            try {
//                this.videoPartFrameDynamoDBService.saveFrame(i + 1,
//                        new VideoPartFrame(1, 0, Optional.empty()));
//            } catch (DynamoDbServiceException e) {
//                e.printStackTrace();
//            }
//        }
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

        // Fetch or load video into cache
        String videoKey = buildVideoKeyString(nextVideoPartToUse, cameraId);
        File videoFile = videoCache.get(videoKey, key -> downloadVideoFromS3(key));

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
            String newVideoKey = buildVideoKeyString(nextVideoPartToUse, cameraId);
            videoFile = videoCache.get(newVideoKey, key -> downloadVideoFromS3(key));
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
        StringBuilder videoKeyBuilder = new StringBuilder(VIDEO_FOLDER);
        videoKeyBuilder.append("/video");
        videoKeyBuilder.append(nextVideoPartToUse);
        videoKeyBuilder.append("_");
        videoKeyBuilder.append(cameraId);
        videoKeyBuilder.append(".mp4");

        return videoKeyBuilder.toString();
    }

    private File downloadVideoFromS3(String key) {
        try {
            Path tempFilePath = Files.createTempFile("camera-video-", ".mp4");
            File tempFile = tempFilePath.toFile();
            if (tempFile.exists()) {
                tempFile.delete();
            }

            // Download video from S3 to the temporary file
            s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(key)
                            .build(),
                    tempFilePath
            );

            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to download video from S3: " + key, e);
        }
    }


    // Convert BufferedImage to byte array
    private byte[] convertImageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}

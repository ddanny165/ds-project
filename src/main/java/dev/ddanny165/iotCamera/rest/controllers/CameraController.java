package dev.ddanny165.iotCamera.rest.controllers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ddanny165.iotCamera.models.VideoPartFrame;
import dev.ddanny165.iotCamera.services.VideoPartFrameDynamoDBService;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
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

@RestController
public class CameraController {

    private static final String BUCKET_NAME = "camera-videos-bucket-ds";
    private static final String VIDEO_FOLDER = "edited-videos/output"; // Folder in S3 bucket

    private static final String VIDEO_PATH = "/Users/danylomoskaliuk/Desktop/ds-project/edited-videos/"; // Define the base path for videos

    private final S3Client s3Client;
    private final Cache<String, File> videoCache;

    private VideoPartFrameDynamoDBService videoPartFrameDynamoDBService;

    public final Integer numberOfCameras;

//    public CameraController() {
//        this.s3Client = S3Client.builder()
//                .region(Region.US_EAST_1)
//                .credentialsProvider(DefaultCredentialsProvider.create())
//                .build();
//
//        this.videoCache = Caffeine.newBuilder()
//                .maximumSize(5) // Limit to 10 videos in memory
//                .expireAfterAccess(30, TimeUnit.MINUTES) // Evict after 30 minutes of inactivity
//                .build();
//
//        this.cameraToFrames = new HashMap<>();
//        this.numberOfCameras = 5;
//
//        for (int i = 0; i < numberOfCameras; i++) {
//            this.cameraToFrames.put(i + 1, new VideoPartFrame(1, 0, Optional.empty()));
//        }
//    }

    public CameraController(VideoPartFrameDynamoDBService videoPartFrameDynamoDBService) {
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.videoCache = Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();

        this.videoPartFrameDynamoDBService = videoPartFrameDynamoDBService;
        this.numberOfCameras = 5;
        for (int i = 0; i < numberOfCameras; i++) {
            this.videoPartFrameDynamoDBService.saveFrame(i + 1,
                    new VideoPartFrame(1, 0, Optional.empty()));
        }
    }

    @GetMapping("/camera/{id}/frame")
    public byte[] getVideoFrame(@PathVariable("id") int cameraId) throws IOException {
        if (cameraId < 1 || cameraId > numberOfCameras) {
            throw new IllegalArgumentException("Camera ID is out of bounds");
        }

        LocalDateTime accessedAt = LocalDateTime.now();

//        VideoPartFrame videoPartFrame = cameraToFrames.get(cameraId);
        Optional<VideoPartFrame> videoPartFrameOpt = this.videoPartFrameDynamoDBService.getFrame(cameraId);
        if (videoPartFrameOpt.isEmpty()) {
            System.err.println("ERROR UPON GETTING THE CURRENT FRAME!");
        }
        VideoPartFrame videoPartFrame = videoPartFrameOpt.get();

        Integer nextVideoFrameToUse = videoPartFrame.nextFrameToUse();
        Integer nextVideoPartToUse = videoPartFrame.nextVideoPart();

        String videoKey = VIDEO_FOLDER + "/video" + nextVideoPartToUse + "_" + cameraId + ".mp4";
        // Fetch or load video into cache
        File videoFile = videoCache.get(videoKey, key -> downloadVideoFromS3(key));

        boolean isVideoPartChanged = false;
        if (videoPartFrame.accessedAt().isPresent()) {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();

                int secondsPassed = (int)Duration.between(videoPartFrame.accessedAt().get(), accessedAt).getSeconds();
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
//                cameraToFrames.put(cameraId, new VideoPartFrame(nextVideoPartToUse, nextVideoFrameToUse, Optional.of(accessedAt)));
            }
        }

        if (isVideoPartChanged) {
            String newVideoKey = VIDEO_FOLDER + "/video" + nextVideoPartToUse + "_" + cameraId + ".mp4";
            videoFile = videoCache.get(newVideoKey, key -> downloadVideoFromS3(key));
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();

//            if (nextVideoFrameToUse >= grabber.getLengthInFrames()) {
//                if (nextVideoPartToUse == 11) {
//                    nextVideoPartToUse = 1;
//                } else {
//                    nextVideoPartToUse++;
//                }
//
//                nextVideoFrameToUse = 0;
//                cameraToFrames.put(cameraId, new VideoPartFrame(nextVideoPartToUse, 0, Optional.of(accessedAt)));
//            }
            System.out.println("Using video " + nextVideoPartToUse +
                    "and frame " + nextVideoFrameToUse);
            grabber.setFrameNumber(nextVideoFrameToUse);
            Frame frame = grabber.grabImage();

            BufferedImage bufferedImage = new Java2DFrameConverter().convert(frame);
            videoPartFrameDynamoDBService.saveFrame(cameraId, new VideoPartFrame(nextVideoPartToUse, ++nextVideoFrameToUse, Optional.of(accessedAt)));
//            cameraToFrames.put(cameraId, new VideoPartFrame(nextVideoPartToUse, ++nextVideoFrameToUse, Optional.of(accessedAt)));
            return convertImageToBytes(bufferedImage);
        }
    }

    // Convert a JavaCV Frame to BufferedImage
    private BufferedImage convertToBufferedImage(Frame frame) {
        Java2DFrameConverter converter = new Java2DFrameConverter();
        return converter.convert(frame);  // Use Java2DFrameConverter to convert the Frame to a BufferedImage
    }

    private File downloadVideoFromS3(String key) {
        try {
            // Create a uniquely named temporary file in the default temp directory
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


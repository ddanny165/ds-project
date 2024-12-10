package dev.ddanny165.iotCamera.services;

import dev.ddanny165.iotCamera.exceptions.DynamoDbServiceException;
import dev.ddanny165.iotCamera.models.VideoPartFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CameraService {

    private final Integer numberOfCameras;
    private final VideoPartFrameDynamoDBService videoPartFrameDynamoDBService;

    public CameraService(VideoPartFrameDynamoDBService videoPartFrameDynamoDBService, S3Client s3Client) {
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

        String videoFilePath = buildVideoKeyString(cameraId);
        File videoFile = new File(videoFilePath);

        if (!videoFile.exists()) {
            System.err.println("ERROR: Video file not found at path: " + videoFilePath);
        }

        if (videoPartFrame.accessedAt().isPresent()) {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();

                int secondsPassed = (int) Duration.between(videoPartFrame.accessedAt().get(), accessedAt).getSeconds();
                int framesPerSecond = (int)grabber.getFrameRate();
                int frameOffset = secondsPassed * framesPerSecond;

                nextVideoFrameToUse += frameOffset;
                if (nextVideoFrameToUse >= grabber.getLengthInFrames()) {
                    // then we start with the beginning of the same video again
                    nextVideoFrameToUse = 0;
                }

                videoPartFrameDynamoDBService.saveFrame(cameraId, new VideoPartFrame(nextVideoFrameToUse, Optional.of(accessedAt)));
            }
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();
            grabber.setFrameNumber(nextVideoFrameToUse);
            Frame frame = grabber.grabImage();

            BufferedImage bufferedImage = new Java2DFrameConverter().convert(frame);
            videoPartFrameDynamoDBService.saveFrame(cameraId, new VideoPartFrame(++nextVideoFrameToUse, Optional.of(accessedAt)));
            return convertImageToBytes(bufferedImage);
        }
    }

    private String buildVideoKeyString(Integer cameraId) {
        StringBuilder videoKeyBuilder = new StringBuilder();
        videoKeyBuilder.append("/app/videos");
        videoKeyBuilder.append("/camera");
        videoKeyBuilder.append(cameraId);
        videoKeyBuilder.append(".mp4");

        return videoKeyBuilder.toString();
    }

    // Convert BufferedImage to byte array
    private byte[] convertImageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}

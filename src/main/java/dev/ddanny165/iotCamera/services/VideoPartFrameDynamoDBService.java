package dev.ddanny165.iotCamera.services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.api.PutItemApi;
import com.amazonaws.services.dynamodbv2.document.internal.PutItemImpl;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import dev.ddanny165.iotCamera.mappers.VideoPartFrameDeserializer;
import dev.ddanny165.iotCamera.mappers.VideoPartFrameSerializer;
import dev.ddanny165.iotCamera.models.VideoPartFrame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VideoPartFrameDynamoDBService {

    private final AmazonDynamoDB ddb;
    private final String tableName;

    public VideoPartFrameDynamoDBService(AmazonDynamoDB ddb) {
        this.ddb = ddb;
        this.tableName = "CameraAccesses";
    }

    public void saveFrame(Integer cameraId, VideoPartFrame frame) {
        try {
            String jsonFrame = VideoPartFrameSerializer.toJson(frame);
            Map<String, AttributeValue> itemValues = new HashMap<>();
            AttributeValue keyAtrValue = new AttributeValue();
            keyAtrValue.setN(cameraId.toString());

            itemValues.put("cameraID", keyAtrValue);
            itemValues.put("frameData", new AttributeValue(jsonFrame));

            PutItemRequest request = new PutItemRequest()
                    .withTableName(this.tableName)
                    .withItem(itemValues);

            try {
                ddb.putItem(request);
            } catch (ResourceNotFoundException e) {
                System.err.format("Error: The table \"%s\" can't be found.\n", this.tableName);
                System.err.println("Be sure that it exists and that you've typed its name correctly!");
                System.exit(1);
            } catch (AmazonServiceException e) {
                System.err.println(e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error upon marshalling the videopartframe occured");
        }
    }

    public Optional<VideoPartFrame> getFrame(Integer cameraId) {
        AttributeValue keyAtrValue = new AttributeValue();
        keyAtrValue.setN(cameraId.toString());

        Map<String, AttributeValue> key = Map.of("cameraID", keyAtrValue);

        GetItemRequest request = new GetItemRequest()
                .withTableName(this.tableName)
                .withKey(key);

        Map<String, AttributeValue> item = ddb.getItem(request).getItem();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }

        try {
            VideoPartFrame videoPartFrame = VideoPartFrameDeserializer.fromJson(item.get("frameData").getS());
//            if (videoPartFrame.accessedAt() == null) {
//                return Optional.of(new VideoPartFrame(videoPartFrame.nextVideoPart(),
//                        videoPartFrame.nextFrameToUse(),
//                        Optional.empty()));
//            }

            return Optional.of(videoPartFrame);
        } catch (Exception e) {
            System.err.println("Error occurred upon unmarshalling the videoPartFrame object");
            return Optional.empty();
        }
    }
}


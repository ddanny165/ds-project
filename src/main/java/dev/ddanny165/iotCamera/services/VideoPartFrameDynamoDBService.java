package dev.ddanny165.iotCamera.services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import dev.ddanny165.iotCamera.exceptions.DynamoDbServiceException;
import dev.ddanny165.iotCamera.mappers.VideoPartFrameDeserializer;
import dev.ddanny165.iotCamera.mappers.VideoPartFrameSerializer;
import dev.ddanny165.iotCamera.models.VideoPartFrame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VideoPartFrameDynamoDBService {

    private final AmazonDynamoDB ddb;

    @Value("${DB_TABLE_NAME}")
    private String tableName;

    public VideoPartFrameDynamoDBService(AmazonDynamoDB ddb) {
        this.ddb = ddb;
    }

    public void saveFrame(Integer cameraId, VideoPartFrame frame) throws DynamoDbServiceException {
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
                throw new DynamoDbServiceException(String.format("Error: The table \"%s\" can't be found.\n",
                        this.tableName),e);
            } catch (AmazonServiceException e) {
                System.err.println(e.getMessage());
                throw new DynamoDbServiceException(e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new DynamoDbServiceException("Error upon marshalling the VideoPartFrame object occurred", e);
        }
    }

    public Optional<VideoPartFrame> getFrame(Integer cameraId) throws DynamoDbServiceException {
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
            return Optional.of(videoPartFrame);
        } catch (Exception e) {
            throw new DynamoDbServiceException("Error upon unmarshalling the VideoPartFrame object occurred", e);
        }
    }
}


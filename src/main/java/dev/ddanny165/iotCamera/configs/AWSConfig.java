package dev.ddanny165.iotCamera.configs;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWSConfig {
    @Bean
    public AmazonDynamoDB dynamoDBClient() {
        return AmazonDynamoDBClient.builder()
                .withRegion(Regions.US_EAST_1)
                .build();
    }
}

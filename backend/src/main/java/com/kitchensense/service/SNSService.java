package com.kitchensense.service;

import com.kitchensense.model.FoodItem;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.List;

public class SNSService {

    // Read from Lambda environment variable, not hardcoded. This means we can change the SNS topic without redeploying code. You will set this environment variable in the AWS Console after creating the SNS topic.
    private static final String TOPIC_ARN = System.getenv("SNS_TOPIC_ARN");

    // Created once and reused — same reasoning as DynamoDBService. SDK clients are thread-safe and expensive to initialize.
    private final SnsClient snsClient;

    public SNSService() {
        // Explicitly using UrlConnectionHttpClient for Lambda runtime compatibility since Netty is absent by default
        this.snsClient = SnsClient.builder()
                .region(Region.AP_SOUTH_1)
                .httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().build())
                .build();
    }

    public void sendAlert(String subject, String message) {
        PublishRequest request = PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .subject(subject)
                .message(message)
                .build();

        // SNS delivers this message to ALL subscribers of the topic — both email and SMS subscribers receive it simultaneously. The subject field is only used for email subscribers; SMS subscribers receive only the message body.
        PublishResponse response = snsClient.publish(request);
        System.out.println("SNS alert sent, messageId: " + response.messageId());
    }

    public void sendExpiryAlertForItems(List<FoodItem> items) {
        // Guard against empty list — publishing an empty alert would confuse users and waste SNS quota.
        if (items == null || items.isEmpty()) {
            return;
        }

        String subject = "🥗 KitchenSense: " + items.size() + " item(s) expiring soon!";

        StringBuilder sb = new StringBuilder();
        sb.append("KitchenSense Alert — Items expiring soon!\n\n");
        sb.append("The following items need your attention:\n\n");

        for (FoodItem item : items) {
            sb.append("• ")
              .append(item.getItemName())
              .append(" (")
              .append(item.getItemType())
              .append(") — expires: ")
              .append(item.getEffectiveExpiry())
              .append(" — qty: ")
              .append(item.getQuantity())
              .append("\n");
        }

        sb.append("\nPlease consume or discard these items to avoid waste.\n\n— KitchenSense");

        sendAlert(subject, sb.toString());
    }
}
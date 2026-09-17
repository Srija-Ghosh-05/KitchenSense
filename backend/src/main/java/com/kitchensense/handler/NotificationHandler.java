package com.kitchensense.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.kitchensense.model.FoodItem;
import com.kitchensense.service.DynamoDBService;
import com.kitchensense.service.SNSService;

import java.util.List;

// This handler uses ScheduledEvent as input type because it is triggered by AWS 
// EventBridge (CloudWatch Events), not by API Gateway. EventBridge sends a JSON event 
// with schedule metadata — we don't use the event data itself, just the trigger.
// Output is String — a simple status message returned to EventBridge for logging.
public class NotificationHandler implements RequestHandler<ScheduledEvent, String> {

    // For the hackathon, we notify one fixed user. In production, you would store all user IDs 
    // in a DynamoDB table and loop through them. USER_ID is read from a Lambda environment 
    // variable so it can be changed without redeploying code.
    private static final String USER_ID = System.getenv("NOTIFICATION_USER_ID") != null
            ? System.getenv("NOTIFICATION_USER_ID")
            : "user001";

    // Fields are initialized at class level to persist across warm invocations (cold-start optimization)
    private final DynamoDBService dynamoDBService = new DynamoDBService();
    private final SNSService snsService = new SNSService();

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        // event.getTime() returns the scheduled trigger time from EventBridge. Logging 
        // this helps verify the cron schedule is firing at the correct time in CloudWatch Logs.
        context.getLogger().log("NotificationHandler triggered. Scheduled time: " + event.getTime());

        try {
            // 2 days gives users enough time to act — they see the alert today and can use or 
            // discard the item tomorrow. 1 day is too late; 3 days creates alert fatigue.
            List<FoodItem> expiringItems = dynamoDBService.getExpiringItems(USER_ID, 2);

            // We skip the SNS publish entirely when nothing is expiring. Sending an empty 
            // alert would confuse users and waste SNS quota.
            if (expiringItems.isEmpty()) {
                context.getLogger().log("No expiring items found for user: " + USER_ID + ". No alert sent.");
                return "No alerts sent. Kitchen is in good shape!";
            }

            context.getLogger().log("Found " + expiringItems.size() + " expiring items for user: " + USER_ID + ". Sending SNS alert.");

            // Dispatch SMS/Email notifications via AWS SNS using preconfigured notification topic
            snsService.sendExpiryAlertForItems(expiringItems);

            String result = "Alert sent for " + expiringItems.size() + " expiring item(s) for user: " + USER_ID;
            context.getLogger().log(result);
            return result;

        } catch (Exception e) {
            // We return an error string rather than throwing — EventBridge will log the 
            // return value. Throwing an exception would cause EventBridge to retry the 
            // invocation, which we don't want for a notification that already partially ran.
            context.getLogger().log("Error in NotificationHandler: " + e.getMessage());
            return "Error sending notification: " + e.getMessage();
        }
    }
}
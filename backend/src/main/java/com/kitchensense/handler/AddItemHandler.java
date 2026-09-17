package com.kitchensense.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchensense.model.FoodItem;
import com.kitchensense.service.DynamoDBService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// RequestHandler is Lambda's interface. 
// INPUT: APIGatewayProxyRequestEvent — API Gateway converts the HTTP request into this Java object before calling us.
// OUTPUT: APIGatewayProxyResponseEvent — we build this and API Gateway converts it back to an HTTP response.
public class AddItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Fields are initialized at class level, not inside handleRequest(). Lambda reuses the same instance across warm invocations — so these are created only once on cold start.
    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDBService dynamoDBService = new DynamoDBService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // context.getLogger() writes to CloudWatch Logs. Always use this for logging in Lambda — System.out.println also works but context.getLogger() is the official API.
        context.getLogger().log("Processing AddItemHandler request...");

        // CORS headers are required because our HTML frontend is on a different domain than the API Gateway URL. Without these headers, the browser blocks the response. Allow-Origin * means any domain can call this API — acceptable for a hackathon.
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Browsers send an OPTIONS preflight request before every cross-origin POST/DELETE to check if CORS is allowed. We must respond 200 to OPTIONS or the actual request will never be sent.
        if ("OPTIONS".equalsIgnoreCase(input.getHttpMethod())) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody("");
        }

        try {
            // input.getBody() returns the raw JSON string from the HTTP request body. Jackson's readValue() converts it into a FoodItem object using the field names as keys. If a field in JSON doesn't match any FoodItem field, it is ignored because of @JsonIgnoreProperties(ignoreUnknown=true).
            FoodItem item = mapper.readValue(input.getBody(), FoodItem.class);

            // Validate required fields
            if (item.getItemName() == null || item.getItemName().isBlank() ||
                item.getItemType() == null || item.getItemType().isBlank()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{\"error\": \"itemName and itemType are required\"}");
            }

            // Validate type-specific fields
            if ("PACKAGED".equalsIgnoreCase(item.getItemType())) {
                if (item.getExpiryDate() == null || item.getExpiryDate().isBlank()) {
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"expiryDate is required for PACKAGED items\"}");
                }
            } else if ("HOMEMADE".equalsIgnoreCase(item.getItemType())) {
                if (item.getCookDate() == null || item.getCookDate().isBlank() || item.getShelfLifeDays() == null) {
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(headers)
                            .withBody("{\"error\": \"cookDate and shelfLifeDays are required for HOMEMADE items\"}");
                }
            }

            // UUID.randomUUID() generates a globally unique identifier. This becomes the DynamoDB sort key. Using UUID means no two items can ever collide even across millions of users.
            item.setItemId(UUID.randomUUID().toString());

            // Instant.now() gives UTC timestamp in ISO-8601 format e.g. 2026-09-13T10:30:00Z. Stored as String in DynamoDB for simplicity.
            item.setCreatedAt(Instant.now().toString());

            // In production, userId would come from the JWT token validated by Cognito authorizer. For the hackathon, we accept it as a query parameter with 'user001' as fallback. This allows testing without full auth setup.
            Map<String, String> queryParams = input.getQueryStringParameters();
            String userId = (queryParams != null && queryParams.containsKey("userId"))
                    ? queryParams.get("userId")
                    : "user001";
            item.setUserId(userId);

            // Save item into DynamoDB
            dynamoDBService.addItem(item);

            // 201 Created is the correct HTTP status for a successful resource creation. We return the saved item so the frontend can immediately display it without making another GET request.
            String responseBody = mapper.writeValueAsString(item);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(headers)
                    .withBody(responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error in AddItemHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
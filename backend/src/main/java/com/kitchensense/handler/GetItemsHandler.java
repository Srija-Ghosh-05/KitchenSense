package com.kitchensense.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchensense.model.FoodItem;
import com.kitchensense.service.DynamoDBService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// RequestHandler is Lambda's interface. 
// INPUT: APIGatewayProxyRequestEvent — API Gateway converts the HTTP request into this Java object before calling us.
// OUTPUT: APIGatewayProxyResponseEvent — we build this and API Gateway converts it back to an HTTP response.
public class GetItemsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Fields are initialized at class level to persist across warm invocations (cold-start optimization)
    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDBService dynamoDBService = new DynamoDBService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing GetItemsHandler request...");

        // CORS headers enable cross-origin browser access from the web application interface
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Handle CORS preflight check for options protocol
        if ("OPTIONS".equalsIgnoreCase(input.getHttpMethod())) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody("");
        }

        try {
            // Extract target user ID from request parameters with a default fallback for simplified API usage
            Map<String, String> queryParams = input.getQueryStringParameters();
            String userId = (queryParams != null && queryParams.containsKey("userId"))
                    ? queryParams.get("userId")
                    : "user001";

            // Fetch stored food items pre-sorted by effective expiry date
            List<FoodItem> items = dynamoDBService.getItemsByUser(userId);

            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            List<Map<String, Object>> enrichedItems = new ArrayList<>();

            for (FoodItem item : items) {
                Map<String, Object> itemMap = new HashMap<>();

                // Include all non-null fields from the raw domain model
                if (item.getItemId() != null) itemMap.put("itemId", item.getItemId());
                if (item.getUserId() != null) itemMap.put("userId", item.getUserId());
                if (item.getItemName() != null) itemMap.put("itemName", item.getItemName());
                if (item.getItemType() != null) itemMap.put("itemType", item.getItemType());
                if (item.getQuantity() != null) itemMap.put("quantity", item.getQuantity());
                if (item.getStorageLocation() != null) itemMap.put("storageLocation", item.getStorageLocation());
                if (item.getExpiryDate() != null) itemMap.put("expiryDate", item.getExpiryDate());
                if (item.getCookDate() != null) itemMap.put("cookDate", item.getCookDate());
                if (item.getShelfLifeDays() != null) itemMap.put("shelfLifeDays", item.getShelfLifeDays());
                if (item.getCreatedAt() != null) itemMap.put("createdAt", item.getCreatedAt());
                if (item.getFridgeStatus() != null) itemMap.put("fridgeStatus", item.getFridgeStatus());

                String effectiveExpiry = item.getEffectiveExpiry();
                if (effectiveExpiry != null) {
                    itemMap.put("effectiveExpiry", effectiveExpiry);
                }

                // consumeFirst flag tells the frontend which items to highlight in the urgent banner. 
                // Items expiring today or tomorrow need immediate attention. We compute this on the backend so the frontend logic stays simple — it just checks the boolean.
                boolean consumeFirst = false;
                if (effectiveExpiry != null) {
                    LocalDate expiryLocalDate = LocalDate.parse(effectiveExpiry);
                    if (!expiryLocalDate.isAfter(tomorrow)) {
                        consumeFirst = true;
                    }
                }
                itemMap.put("consumeFirst", consumeFirst);

                // daysUntilExpiry is a convenience field for the frontend. Negative means already expired. 
                // 0 means expires today. 1 means tomorrow. Frontend uses this to color-code urgency without doing date math in JavaScript.
                if (effectiveExpiry != null) {
                    long days = ChronoUnit.DAYS.between(today, LocalDate.parse(effectiveExpiry));
                    itemMap.put("daysUntilExpiry", (int) days);
                } else {
                    itemMap.put("daysUntilExpiry", null);
                }

                enrichedItems.add(itemMap);
            }

            String responseBody = mapper.writeValueAsString(enrichedItems);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error in GetItemsHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
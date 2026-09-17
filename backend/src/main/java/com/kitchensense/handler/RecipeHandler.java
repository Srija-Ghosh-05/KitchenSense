package com.kitchensense.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchensense.model.FoodItem;
import com.kitchensense.service.BedrockService;
import com.kitchensense.service.DynamoDBService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// RequestHandler is Lambda's interface.
// INPUT: APIGatewayProxyRequestEvent — API Gateway converts the HTTP request into this Java object before calling us.
// OUTPUT: APIGatewayProxyResponseEvent — we build this and API Gateway converts it back to an HTTP response.
public class RecipeHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Fields are initialized at class level to persist across warm invocations (cold-start optimization)
    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDBService dynamoDBService = new DynamoDBService();
    private final BedrockService bedrockService = new BedrockService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing RecipeHandler request...");

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
            // Extract query parameters for dynamic recipe tailoring
            Map<String, String> queryParams = input.getQueryStringParameters();

            String userId = queryParams != null && queryParams.containsKey("userId")
                    ? queryParams.get("userId")
                    : "user001";

            // fridgeStatus comes from the user's Cognito profile — the frontend reads it after login and passes it as a query parameter here. 
            // This tells Bedrock whether to suggest recipes that need refrigeration or not.
            String fridgeStatus = queryParams != null && queryParams.containsKey("fridgeStatus")
                    ? queryParams.get("fridgeStatus")
                    : "FUNCTIONAL";

            // We use 3 days instead of 2 for recipe suggestions — gives slightly more ingredients to work with, making recipe suggestions more useful. SNS alerts still use 2 days.
            List<FoodItem> expiringItems = dynamoDBService.getExpiringItems(userId, 3);

            // Return 200 not 404 — this is not an error, just a happy state. The frontend should show a positive message, not an error banner.
            if (expiringItems.isEmpty()) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("recipe", "Great news! Nothing is expiring soon. Your kitchen is in good shape! Check back when items are closer to expiry for personalised recipe suggestions.");
                emptyResponse.put("hasExpiring", false);

                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(headers)
                        .withBody(mapper.writeValueAsString(emptyResponse));
            }

            // Call Claude 3 Haiku via Bedrock to generate recipe tailored to expiring items and household fridge constraints
            String recipe = bedrockService.suggestRecipe(expiringItems, fridgeStatus);

            // We include itemCount and fridgeStatus in the response so the frontend can display context like 'Based on 3 expiring items' without needing to make another API call.
            Map<String, Object> response = new HashMap<>();
            response.put("recipe", recipe);
            response.put("hasExpiring", true);
            response.put("itemCount", expiringItems.size());
            response.put("fridgeStatus", fridgeStatus);

            String responseBody = mapper.writeValueAsString(response);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error in RecipeHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
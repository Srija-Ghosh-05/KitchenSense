package com.kitchensense.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.kitchensense.service.DynamoDBService;

import java.util.HashMap;
import java.util.Map;

// RequestHandler is Lambda's interface.
// INPUT: APIGatewayProxyRequestEvent — API Gateway converts the HTTP request into this Java object before calling us.
// OUTPUT: APIGatewayProxyResponseEvent — we build this and API Gateway converts it back to an HTTP response.
public class DeleteItemHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Fields are initialized at class level to persist across warm invocations (cold-start optimization)
    private final DynamoDBService dynamoDBService = new DynamoDBService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing DeleteItemHandler request...");

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
            // Path parameters come from the URL path itself.
            // For route DELETE /items/{id}, API Gateway extracts {id} and puts it in pathParameters map with key 'id'. 
            // This is different from query parameters which come after '?' in URL.
            Map<String, String> pathParams = input.getPathParameters();
            if (pathParams == null || !pathParams.containsKey("id")) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{\"error\": \"Item ID is required in path\"}");
            }

            String itemId = pathParams.get("id");
            if (itemId == null || itemId.isBlank()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{\"error\": \"Item ID is required in path\"}");
            }

            // userId identifies which user's item to delete.
            // We verify ownership implicitly — DynamoDB delete requires both partition key (userId) AND sort key (itemId). 
            // If userId doesn't match, the delete silently does nothing, which is safe behavior.
            Map<String, String> queryParams = input.getQueryStringParameters();
            String userId = (queryParams != null && queryParams.containsKey("userId"))
                    ? queryParams.get("userId")
                    : "user001";

            context.getLogger().log("Deleting item: " + itemId + " for user: " + userId);

            dynamoDBService.deleteItem(userId, itemId);

            // We return the deleted itemId in the response so the frontend can immediately remove that specific card from the UI without refetching the entire list. This makes the UI feel instant.
            String responseBody = String.format("{\"message\": \"Item deleted successfully\", \"itemId\": \"%s\"}", itemId);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error in DeleteItemHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
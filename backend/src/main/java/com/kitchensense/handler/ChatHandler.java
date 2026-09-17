package com.kitchensense.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
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
public class ChatHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Fields are initialized at class level to persist across warm invocations (cold-start optimization)
    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDBService dynamoDBService = new DynamoDBService();
    private final BedrockService bedrockService = new BedrockService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Processing ChatHandler request...");

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
            // Chat requests come as POST with a JSON body containing the user's message.
            // We parse it with Jackson rather than string splitting to safely handle special characters, unicode, and quotes in the message.
            String body = input.getBody();
            if (body == null || body.isBlank()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{\"error\": \"Request body is required\"}");
            }

            JsonNode bodyNode = mapper.readTree(body);
            String userMessage = bodyNode.has("message") ? bodyNode.get("message").asText() : null;

            if (userMessage == null || userMessage.isBlank()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(headers)
                        .withBody("{\"error\": \"message field is required in request body\"}");
            }

            // Extract query parameters for user identity and fridge status constraints
            Map<String, String> queryParams = input.getQueryStringParameters();

            String userId = queryParams != null && queryParams.containsKey("userId")
                    ? queryParams.get("userId")
                    : "user001";

            String fridgeStatus = queryParams != null && queryParams.containsKey("fridgeStatus")
                    ? queryParams.get("fridgeStatus")
                    : "FUNCTIONAL";

            // We pass the full inventory to Bedrock so the AI can answer questions like 'what should I cook tonight?' or 'my fridge is full, what to remove?' with actual knowledge of what the user has. This is what makes the chatbot genuinely useful rather than generic.
            List<FoodItem> inventory = dynamoDBService.getItemsByUser(userId);

            // Call Bedrock chat service passing user prompt, context inventory, and current appliance status
            String aiResponse = bedrockService.chat(userMessage, inventory, fridgeStatus);

            // We use 'reply' as the key (not 'message' or 'response') to clearly distinguish the AI's reply from the user's original message field in the request body.
            Map<String, Object> response = new HashMap<>();
            response.put("reply", aiResponse);
            response.put("userId", userId);

            String responseBody = mapper.writeValueAsString(response);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error in ChatHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}
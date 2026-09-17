package com.kitchensense.service;

import com.kitchensense.model.FoodItem;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.core.SdkBytes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.Collectors;

public class BedrockService {

    // Using Amazon Titan Text Express — it is available in ap-south-1 (Mumbai) region and is free tier eligible. Claude models require separate access requests. Titan is sufficient for recipe suggestions and kitchen chat.
    private static final String MODEL_ID = "amazon.titan-text-express-v1";

    private final BedrockRuntimeClient bedrockClient;
    // Shared instance — ObjectMapper is thread-safe and expensive to create. Reuse across calls.
    private final ObjectMapper objectMapper;

    public BedrockService() {
        // Explicitly configuration of UrlConnectionHttpClient to ensure execution within Java 21 AWS Lambda environment
        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.AP_SOUTH_1)
                .httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().build())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Bedrock's InvokeModelRequest requires the prompt to be wrapped in a model-specific JSON format and sent as raw bytes. Each model has a different input/output schema. For Titan Text Express, the input schema is:
    // {
    //   "inputText": "your prompt here",
    //   "textGenerationConfig": {
    //     "maxTokenCount": 512,
    //     "temperature": 0.7,
    //     "topP": 0.9
    //   }
    // }
    // temperature 0.7 means moderately creative — not too random, not too rigid. Good for recipes.
    // maxTokenCount 512 is enough for a recipe suggestion without being wasteful.
    private String invokeModel(String prompt) {
        try {
            // Escaping quotes properly so prompt strings don't break JSON structure
            String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"");
            String requestBody = "{"
                    + "\"inputText\": \"" + escapedPrompt + "\","
                    + "\"textGenerationConfig\": {"
                    + "\"maxTokenCount\": 512,"
                    + "\"temperature\": 0.7,"
                    + "\"topP\": 0.9"
                    + "}"
                    + "}";

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(MODEL_ID)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);

            // Titan Text response schema:
            // {
            //   "results": [
            //     { "outputText": "the generated text" }
            //   ]
            // }
            // We read results[0].outputText to get the actual text response.
            String responseJson = response.body().asUtf8String();
            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode resultsArray = rootNode.get("results");

            if (resultsArray != null && resultsArray.isArray() && resultsArray.size() > 0) {
                JsonNode firstResult = resultsArray.get(0);
                if (firstResult.has("outputText")) {
                    return firstResult.get("outputText").asText();
                }
            }
            return "Sorry, I could not generate a suggestion right now. Please try again.";
        } catch (Exception e) {
            System.err.println("Error invoking Bedrock model: " + e.getMessage());
            return "Sorry, I could not generate a suggestion right now. Please try again.";
        }
    }

    public String suggestRecipe(List<FoodItem> expiringItems, String fridgeStatus) {
        // Collect expiring items into a single context string to give context to Titan
        String itemsSummary = expiringItems.stream()
                .map(item -> item.getItemName() + " (expires: " + item.getEffectiveExpiry() + ", qty: " + item.getQuantity() + ")")
                .collect(Collectors.joining(", "));

        String status = (fridgeStatus != null) ? fridgeStatus : "FUNCTIONAL";

        String prompt = "You are a helpful kitchen assistant for an Indian household. "
                + "Suggest ONE practical zero-waste Indian recipe using the ingredients that are expiring soon. Be specific and concise.\n\n"
                + "Expiring ingredients: " + itemsSummary + "\n\n"
                + "Household fridge status: " + status + "\n"
                + "- FUNCTIONAL: has a working refrigerator\n"
                + "- OLD: has an old or weak refrigerator, food spoils faster than usual\n"
                + "- NONE: no refrigerator, all food is at room temperature\n\n"
                + "Based on the fridge status '" + status + "', adjust your recipe urgency accordingly.\n\n"
                + "Suggest a recipe that:\n"
                + "1. Uses the expiring ingredients listed above\n"
                + "2. Is a common Indian dish or snack\n"
                + "3. Can be made within 30 minutes\n"
                + "4. Minimizes food waste\n\n"
                + "Respond with:\n"
                + "Recipe name: [name]\n"
                + "Preparation time: [X minutes]\n"
                + "Ingredients needed: [list]\n"
                + "Quick steps: [3-4 short steps]\n"
                + "Why this recipe: [one sentence on why this uses the expiring food well]";

        return invokeModel(prompt);
    }

    public String chat(String userMessage, List<FoodItem> currentInventory, String fridgeStatus) {
        // Format current inventory details for grounding response
        String inventorySummary;
        if (currentInventory == null || currentInventory.isEmpty()) {
            inventorySummary = "No items currently tracked.";
        } else {
            inventorySummary = currentInventory.stream()
                    .map(item -> item.getItemName() + " (expires: " + item.getEffectiveExpiry() + ", qty: " + item.getQuantity() + ")")
                    .collect(Collectors.joining(", "));
        }

        String status = (fridgeStatus != null) ? fridgeStatus : "FUNCTIONAL";

        String prompt = "You are KitchenSense AI — a helpful, friendly kitchen assistant for Indian households. "
                + "You help with food storage, expiry management, recipes, and reducing food waste.\n\n"
                + "Current kitchen inventory:\n" + inventorySummary + "\n\n"
                + "Household fridge status: " + status + "\n"
                + "- FUNCTIONAL: working refrigerator available\n"
                + "- OLD: old or weak refrigerator\n"
                + "- NONE: no refrigerator\n\n"
                + "User's question: " + userMessage + "\n\n"
                + "Instructions:\n"
                + "- Answer in a warm, helpful, conversational tone\n"
                + "- Keep response under 150 words\n"
                + "- Give practical advice specific to Indian kitchens and Indian ingredients\n"
                + "- If the question is about storage, consider the fridge status above\n"
                + "- If you suggest a recipe, make it Indian\n"
                + "- Do not mention that you are an AI model";

        return invokeModel(prompt);
    }
}
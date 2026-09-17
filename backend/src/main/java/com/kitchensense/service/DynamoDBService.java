package com.kitchensense.service;

import com.kitchensense.model.FoodItem;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;

public class DynamoDBService {

    // This must exactly match the DynamoDB table name you create in the AWS Console
    private static final String TABLE_NAME = "KitchenSenseItems";

    // Created once at class level and reused across warm Lambda invocations. Creating a new client per invocation is expensive.
    private final DynamoDbClient dynamoDB;

    public DynamoDBService() {
        // UrlConnectionHttpClient is required in Lambda because Lambda does not include Netty by default.
        // Without this, AWS SDK v2 throws SdkClientException at runtime.
        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.AP_SOUTH_1)
                .httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().build())
                .build();
    }

    public void addItem(FoodItem item) {
        Map<String, AttributeValue> itemMap = new HashMap<>();

        // Always include compulsory attributes to guarantee structural integrity of the DynamoDB item
        itemMap.put("userId", AttributeValue.fromS(item.getUserId()));
        itemMap.put("itemId", AttributeValue.fromS(item.getItemId()));
        itemMap.put("itemName", AttributeValue.fromS(item.getItemName()));
        itemMap.put("itemType", AttributeValue.fromS(item.getItemType()));
        itemMap.put("quantity", AttributeValue.fromS(item.getQuantity()));
        itemMap.put("createdAt", AttributeValue.fromS(item.getCreatedAt()));

        // DynamoDB does not store null values — omitting an attribute is the correct approach.
        // Storing null would throw a ValidationException.
        if (item.getStorageLocation() != null) {
            itemMap.put("storageLocation", AttributeValue.fromS(item.getStorageLocation()));
        }
        if (item.getExpiryDate() != null) {
            itemMap.put("expiryDate", AttributeValue.fromS(item.getExpiryDate()));
        }
        if (item.getCookDate() != null) {
            itemMap.put("cookDate", AttributeValue.fromS(item.getCookDate()));
        }
        if (item.getFridgeStatus() != null) {
            itemMap.put("fridgeStatus", AttributeValue.fromS(item.getFridgeStatus()));
        }
        if (item.getShelfLifeDays() != null) {
            itemMap.put("shelfLifeDays", AttributeValue.fromN(String.valueOf(item.getShelfLifeDays())));
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(itemMap)
                .build();

        dynamoDB.putItem(request);
    }

    public List<FoodItem> getItemsByUser(String userId) {
        // We use expression attribute name #uid because 'userId' could conflict with DynamoDB reserved words. This is a safe practice for all queries.
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#uid", "userId");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":userId", AttributeValue.fromS(userId));

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("#uid = :userId")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse response = dynamoDB.query(queryRequest);

        List<FoodItem> items = response.items().stream()
                .map(this::mapToFoodItem)
                .collect(Collectors.toList());

        // Sort items by effective expiry date ascending (soonest expiring first).
        // Using nullsLast ensures items with missing dates do not break the comparison or sort out of context.
        items.sort(Comparator.comparing(FoodItem::getEffectiveExpiry, Comparator.nullsLast(Comparator.naturalOrder())));

        return items;
    }

    public void deleteItem(String userId, String itemId) {
        // DynamoDB requires the COMPLETE primary key for delete. Our table uses userId as partition key and itemId as sort key — both are required.
        Map<String, AttributeValue> keyMap = new HashMap<>();
        keyMap.put("userId", AttributeValue.fromS(userId));
        keyMap.put("itemId", AttributeValue.fromS(itemId));

        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(keyMap)
                .build();

        dynamoDB.deleteItem(request);
    }

    public List<FoodItem> getExpiringItems(String userId, int withinDays) {
        // We filter in Java rather than DynamoDB because expiry date is not a key attribute. A DynamoDB filter expression would still scan all items — filtering in Java is equivalent and simpler for our scale.
        List<FoodItem> userItems = getItemsByUser(userId);
        return userItems.stream()
                .filter(item -> item.isExpiringSoon(withinDays))
                .collect(Collectors.toList());
    }

    // We check containsKey() before reading optional fields because DynamoDB does not store nulls — missing attribute means the field was null when saved. Calling .get() on a missing key returns null, and calling .s() on null throws NullPointerException.
    private FoodItem mapToFoodItem(Map<String, AttributeValue> attributeMap) {
        FoodItem item = new FoodItem();

        if (attributeMap.containsKey("itemId")) {
            item.setItemId(attributeMap.get("itemId").s());
        }
        if (attributeMap.containsKey("userId")) {
            item.setUserId(attributeMap.get("userId").s());
        }
        if (attributeMap.containsKey("itemName")) {
            item.setItemName(attributeMap.get("itemName").s());
        }
        if (attributeMap.containsKey("itemType")) {
            item.setItemType(attributeMap.get("itemType").s());
        }
        if (attributeMap.containsKey("quantity")) {
            item.setQuantity(attributeMap.get("quantity").s());
        }
        if (attributeMap.containsKey("createdAt")) {
            item.setCreatedAt(attributeMap.get("createdAt").s());
        }
        if (attributeMap.containsKey("storageLocation")) {
            item.setStorageLocation(attributeMap.get("storageLocation").s());
        }
        if (attributeMap.containsKey("expiryDate")) {
            item.setExpiryDate(attributeMap.get("expiryDate").s());
        }
        if (attributeMap.containsKey("cookDate")) {
            item.setCookDate(attributeMap.get("cookDate").s());
        }
        if (attributeMap.containsKey("fridgeStatus")) {
            item.setFridgeStatus(attributeMap.get("fridgeStatus").s());
        }
        if (attributeMap.containsKey("shelfLifeDays")) {
            item.setShelfLifeDays(Integer.parseInt(attributeMap.get("shelfLifeDays").n()));
        }

        return item;
    }
}
package com.kitchensense.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FoodItem {

    private String itemId;
    private String userId;
    private String itemName;
    private String itemType;
    private String quantity;
    private String storageLocation;
    private String expiryDate;
    private String cookDate;
    private Integer shelfLifeDays;
    private String createdAt;
    private String fridgeStatus;

    public FoodItem() {
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getCookDate() {
        return cookDate;
    }

    public void setCookDate(String cookDate) {
        this.cookDate = cookDate;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setShelfLifeDays(Integer shelfLifeDays) {
        this.shelfLifeDays = shelfLifeDays;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFridgeStatus() {
        return fridgeStatus;
    }

    public void setFridgeStatus(String fridgeStatus) {
        this.fridgeStatus = fridgeStatus;
    }

    public String getEffectiveExpiry() {
        if ("PACKAGED".equalsIgnoreCase(itemType)) {
            return expiryDate;
        }
        if ("HOMEMADE".equalsIgnoreCase(itemType) && cookDate != null && shelfLifeDays != null) {
            LocalDate cookLocalDate = LocalDate.parse(cookDate);
            return cookLocalDate.plusDays(shelfLifeDays).toString();
        }
        return null;
    }

    @JsonIgnore
    public boolean isExpiringSoon(int withinDays) {
        String effective = getEffectiveExpiry();
        if (effective == null) {
            return false;
        }
        LocalDate expiry = LocalDate.parse(effective);
        LocalDate threshold = LocalDate.now().plusDays(withinDays);
        return !expiry.isAfter(threshold);
    }
}
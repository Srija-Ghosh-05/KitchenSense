# KitchenSense 🥘

> Zero-waste kitchen management for Indian households — track expiry, reduce waste, cook smarter with AI.

[![Built on AWS](https://img.shields.io/badge/Built%20on-AWS-orange)](https://aws.amazon.com)
[![Java](https://img.shields.io/badge/Backend-Java%2021-red)](https://www.java.com)
[![Blog](https://img.shields.io/badge/Blog-AWS%20Builder%20Center-blue)](https://builder.aws.com/content/3JaKXC0KhnP8AYqeJFmZveBCQZX/how-i-built-a-full-stack-ai-app-on-aws-in-4-days-as-a-complete-aws-beginner)

---

## The Problem

Every Indian household wastes food every single day. Milk goes sour, dal gets forgotten, vegetables rot before they're used. India wastes **68 million tonnes of food annually** — not because people don't care, but because they simply forget what's in their kitchen and when it expires.

There was no simple, India-specific tool to track kitchen inventory, warn about expiry, and suggest recipes to use up food before it goes bad. KitchenSense is that tool.

---

## What is KitchenSense?

KitchenSense is a full-stack, AI-powered kitchen management web app built specifically for Indian households. It lets you:

- Track packaged and homemade food items with expiry dates
- Get warned before items expire via a live dashboard
- Receive AI-generated Indian recipes using your expiring ingredients
- Chat with an AI kitchen assistant that knows your pantry
- Get daily email alerts for expiring items via SNS

---

## Demo

**Demo flow:**
1. Sign up with your email
2. Select your fridge status (calibrates shelf life advice)
3. Add items from Add Item page
4. View dashboard — Consume First banner, stats, AI recipe
5. Chat with AI about your kitchen
6. Mark items as consumed

---

## Features

### 🏠 Dashboard
- **Consume First Banner** — highlights items expiring today or tomorrow
- **Live Stats** — expiring soon, good state, fresh cooked, waste saved
- **Priority Staples List** — sorted by soonest expiry with colour-coded urgency
- **Filter tabs** — Refrigerator, Freezer, Dry Pantry
- **Search** — find any item instantly

### ➕ Add Item
- **Packaged food** — enter item name, expiry date, quantity, storage location
- **Homemade food** — enter cook date and shelf life days, expiry calculated automatically
- **Storage Calibration** — fridge status (functional / old / none) adjusts all shelf life advice
- **Quick chips** — one-tap common Indian items and date shortcuts

### 💬 AI Chat
- Powered by **Amazon Bedrock Nova Micro**
- Knows your entire current inventory
- Answers questions about storage, recipes, shelf life
- Calibrated for Indian kitchens and Indian ingredients
- Context panel shows urgent items for quick access

### ✨ Smart Waste Saver
- AI-generated Indian recipe using your expiring ingredients
- Generated in real time on every dashboard load
- Considers your fridge status when suggesting recipes

### 👤 Profile
- Kitchen setup management
- Notification preferences
- Impact stats — food value saved, waste prevented
- Pantry Health Index
- Achievements

### 🔔 Automated Alerts
- Daily email notifications via **Amazon SNS**
- Triggered every night at 2 AM by **Amazon EventBridge**
- Alerts fire only when items are expiring within 48 hours
- Email is waiting in your inbox when you wake up

---

## AWS Architecture

```
Frontend (S3 Static Hosting)
         ↓
API Gateway (REST API — prod stage)
         ↓
Lambda Functions (Java 21, 6 functions)
         ↓
DynamoDB (KitchenSenseItems table)
         ↓
Bedrock Nova Micro (AI features)

EventBridge (Daily cron — 2 AM IST)
         ↓
Lambda KS-Notifications
         ↓
SNS (KitchenSenseAlerts topic)
         ↓
Email
```

### AWS Services Used

| Service | Purpose |
|---|---|
| **S3** | Static website hosting for the frontend |
| **API Gateway** | REST API with 4 resources, CORS enabled |
| **Lambda** | 6 serverless Java 21 functions |
| **DynamoDB** | NoSQL storage for food items (on-demand) |
| **Amazon Bedrock** | Nova Micro AI model for recipes and chat |
| **SNS** | Email alerts for expiring items |
| **EventBridge** | Daily scheduled trigger at 2 AM IST |
| **IAM** | Role-based access control for Lambda |
| **CloudWatch** | Logs for all Lambda functions |

---

## Lambda Functions

| Function | Handler | Purpose |
|---|---|---|
| KS-AddItem | AddItemHandler | POST /items — save new food item |
| KS-GetItems | GetItemsHandler | GET /items — fetch all items with expiry calculation |
| KS-DeleteItem | DeleteItemHandler | DELETE /items/{id} — mark item as consumed |
| KS-Recipe | RecipeHandler | GET /recipe — AI recipe from expiring items |
| KS-Chat | ChatHandler | POST /chat — AI kitchen assistant |
| KS-Notifications | NotificationHandler | Scheduled — daily SNS email alert |

---

## Tech Stack

### Frontend
- Pure HTML, CSS, JavaScript — no framework
- Responsive design — desktop and mobile
- Hosted on AWS S3 static website hosting

### Backend
- Java 21
- AWS Lambda (serverless)
- AWS SDK v2 (2.26.12)
- Jackson for JSON serialization
- Maven for build

### Database
- Amazon DynamoDB
- Table: KitchenSenseItems
- Partition key: userId (String)
- Sort key: itemId (String)
- On-demand capacity mode

### AI
- Amazon Bedrock — Nova Micro v1
- Converse API
- Prompts calibrated for Indian kitchen context
- Fridge status-aware responses

---

## Project Structure

```
KitchenSense/
├── frontend/
│   ├── landing.html
│   ├── auth.html
│   ├── dashboard.html
│   ├── add-item.html
│   ├── chat.html
│   ├── profile.html
│   ├── js/
│   │   └── api.js
│   └── assets/
│       ├── logo-horizontal.png
│       ├── logo-stacked.png
│       ├── characters.png
│       └── empty-state.png
└── backend/
    ├── pom.xml
    └── src/main/java/com/kitchensense/
        ├── handler/
        │   ├── AddItemHandler.java
        │   ├── GetItemsHandler.java
        │   ├── DeleteItemHandler.java
        │   ├── RecipeHandler.java
        │   ├── ChatHandler.java
        │   └── NotificationHandler.java
        ├── model/
        │   └── FoodItem.java
        └── service/
            ├── DynamoDBService.java
            ├── BedrockService.java
            └── SNSService.java
```

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | /prod/items?userId= | Get all items for user |
| POST | /prod/items?userId= | Add new item |
| DELETE | /prod/items/{id}?userId= | Delete item |
| GET | /prod/recipe?userId=&fridgeStatus= | Get AI recipe |
| POST | /prod/chat?userId=&fridgeStatus= | Chat with AI |

---

## Data Model

### FoodItem

| Field | Type | Description |
|---|---|---|
| userId | String | Partition key |
| itemId | String | Sort key (UUID) |
| itemName | String | Name of the food item |
| itemType | String | PACKAGED or HOMEMADE |
| quantity | String | e.g. "1 Packet(s)" |
| storageLocation | String | FRIDGE, FREEZER, or SHELF |
| expiryDate | String | For PACKAGED items (YYYY-MM-DD) |
| cookDate | String | For HOMEMADE items (YYYY-MM-DD) |
| shelfLifeDays | Integer | For HOMEMADE items |
| fridgeStatus | String | FUNCTIONAL, OLD, or NONE |
| createdAt | String | ISO-8601 timestamp |

### Computed Fields (added by GetItemsHandler)

| Field | Type | Description |
|---|---|---|
| effectiveExpiry | String | Calculated expiry date |
| daysUntilExpiry | Integer | Days remaining (negative = expired) |
| consumeFirst | Boolean | True if expiring within 48 hours |

---

## Local Development

### Prerequisites
- Java 21
- Maven 3.8+
- AWS CLI configured

### Build
```bash
cd backend
mvn clean package -DskipTests
```

### Deploy JAR to Lambda
Upload `target/kitchensense-backend-1.0-SNAPSHOT.jar` to each Lambda function via AWS Console.

### Frontend
Open any HTML file directly in browser or host on S3.

---

## 🛠️ AI Tools Used

As per hackathon rules:

- **Claude (Anthropic)** — Architecture design, code review, AWS setup guidance
- **Gemini (Google)** — Code generation
- **Google Stitch** — UI/UX design and mockups
- **ChatGPT (OpenAI)** — Frontend code fixes and image generation

---

## What I Learned

This was my first time using AWS. In one weekend I learned:

- How to design a complete serverless architecture from scratch
- How to write Java Lambda handlers and connect them to API Gateway
- How to use DynamoDB as a NoSQL database with partition and sort keys
- How to integrate Amazon Bedrock's Nova Micro AI model using the Converse API
- How to configure CORS across API Gateway and Lambda
- How to host a static website on S3
- How to set up automated scheduled jobs with EventBridge
- How to send notifications via SNS
- How to debug a live deployed application using CloudWatch logs

---

## Built By

**Srija Ghosh**
B.Tech CSE — Supreme Knowledge Foundation Group of Institutions (MAKAUT), Kolkata
- GitHub: [github.com/Srija-Ghosh-05](https://github.com/Srija-Ghosh-05)
- LinkedIn: [linkedin.com/in/srija-ghosh-929baa325](https://linkedin.com/in/srija-ghosh-929baa325)

---

## Hackathon

Built for **First Commit — Bharat Builds Tour** by WeMakeDevs × AWS
September 17–20, 2026

*Submitted under the **Ship It** track — deployed live on AWS.*
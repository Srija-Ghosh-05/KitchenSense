# KitchenSense 🥘
AI-powered food expiry tracker for Indian households

## Problem
Average Indian household wastes ₹50,000 of food 
annually. KitchenSense tracks expiry dates, suggests 
zero-waste recipes, and sends alerts before food spoils.

## AWS Architecture
- Lambda — 6 serverless Java functions
- DynamoDB — food item storage
- API Gateway — REST API
- Cognito — user authentication
- Bedrock — AI recipe suggestions and chatbot
- SNS — email/SMS expiry alerts
- EventBridge — daily alert scheduler

## Features
- Track packaged and homemade food items
- Fridge-aware AI (adapts to households without fridges)
- AI recipe suggestions from expiring ingredients
- AI kitchen chatbot
- 2-day expiry alerts via email/SMS
- Consume First dashboard

## Built with
Java 21 · AWS Lambda · DynamoDB · Cognito · 
Bedrock · SNS · EventBridge · HTML/CSS/JS

## AI Tools Used
- Claude (architecture, code review, AWS guidance)
- Gemini (code generation)
- Google Stitch (UI design)
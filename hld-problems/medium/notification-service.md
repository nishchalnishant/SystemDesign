# Design Notification Service

> **Difficulty**: Medium  
> **Topics**: Pub-Sub, Message Queues, Third-party Integration, Rate Limiting  
> **Time**: 45 minutes  
> **Companies**: Amazon (SNS), Uber, LinkedIn, Airbnb

## Problem Statement

Design a scalable notification service that sends notifications across multiple channels (Email, SMS, Push Notifications) to millions of users.

**Requirements:**
- **Functional**:
  - Send email, SMS, and push notifications.
  - Support bulk notifications (e.g., "Marketing blast to 1M users").
  - Support transactional notifications (e.g., "OTP", "Order Confirmed").
  - User preferences (Opt-out/Opt-in channels).
  
- **Non-Functional**:
  - **High Availability**: 99.99%
  - **Durability**: No notification lost.
  - **Throughput**: 1M+ notifications/min.
  - **Latency**: Real-time for OTP (<5s), eventual for marketing (<10m).
  - **Deduplication**: Don't send duplicate alerts.

## Architecture

```
Client Services
(Order Service, Marketing)
       ↓
   Load Balancer
       ↓
┌───────────────────┐
│ Notification API  │ (Rate limiting, Validation)
└────────┬──────────┘
         │
         ▼
┌───────────────────┐        ┌──────────────────┐
│  Service Router   │ ──────▶│ User Preferences │
└────────┬──────────┘        │ DB (MySQL/NoSQL) │
         │                   └──────────────────┘
         ▼
    Kafka / RabbitMQ (Decoupling & Buffering)
    topics: [priority-otp], [email], [sms], [push]
         │
         ▼
┌───────────────────┐
│     Workers       │ (Stateless Consumers)
└────────┬──────────┘
         │
    ┌────┴────┬─────────────┐
    ▼         ▼             ▼
  Email      SMS          Push
  Handler    Handler      Handler 
(SendGrid)  (Twilio)     (FCM/APNS)
```

## Data Schema

### User Preferences
```sql
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT FALSE,
    push_enabled BOOLEAN DEFAULT TRUE,
    timezone VARCHAR(50),
    dnd_start_time TIME,
    dnd_end_time TIME
);
```

### Notification Log (Cassandra/DynamoDB)
```sql
CREATE TABLE notification_logs (
    notification_id UUID PRIMARY KEY,
    user_id BIGINT,
    type VARCHAR(20), -- EMAIL/SMS
    status VARCHAR(20), -- SENT/FAILED
    created_at TIMESTAMP,
    third_party_id VARCHAR(100)
);
```

## Key Components

### 1. API & Validation
- **Rate Limit**: Prevent abusive clients (e.g., 100 req/sec per service).
- **Validation**: Check email format, phone number validity.

### 2. Priority Handling (Message Queues)
- **Problem**: Marketing blast (1M emails) blocks critical OTPs.
- **Solution**: Separate Queues!
  - `High_Priority_Queue`: OTPs, Security Alerts (Dedicated Workers).
  - `Low_Priority_Queue`: Marketing, Monthly Statements.

### 3. Deduplication
- **Problem**: Client retries create duplicate notifications.
- **Solution**: 
  - Store `checksum` or unique `request_id` in Redis (TTL 10 mins).
  - Check Redis before enqueuing.

### 4. Third-Party Integration (The "Hard" Part)
- **Challenges**:
  - Vendor Downtime (Twilio down).
  - Rate Limits (Vendor blocks IP).
  - Latency (Vendor slow).
- **Mitigation**:
  - **Retry with Backoff**: Exponential backoff for 5xx errors.
  - **Circuit Breaker**: Stop calling if 50% errors, failover to secondary provider (e.g., SendGrid -> AWS SES).
  - **Rate Limiter (Outbound)**: Throttle requests to match Vendor's TPS limit.

## Scale Estimation

- **Traffic**: 10M notifications/day.
- **Peak**: 1M within 10 mins (Marketing).
- **Storage**:
  - Log retention: 1 year.
  - 1KB per log * 10M/day * 365 = ~3.6 TB/year.
  - Database: Cassandra (Write-heavy, TTL support).

## Interview Tips

**Q: "How do you handle users in Do Not Disturb (DND) mode?"**
- A: "Workers check User Preferences DB before sending. If DND is active, park message in a `Delayed_Queue` with a timestamp to process later."

**Q: "How to prevent spamming users?"**
- A: "Implement a **Frequency Cap** in Redis (e.g., 'User X received 3 marketing emails today -> Drop 4th'). Critical alerts bypass this."

**Q: "What if the worker crashes after sending to Twilio but before updating DB?"**
- A: "Idempotency! Store `third_party_id` in DB. If worker restarts, check if we already have a success ID for this `notification_id`."

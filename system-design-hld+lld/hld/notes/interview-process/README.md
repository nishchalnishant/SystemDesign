---
description: This page talks about the steps to follow for any HLD interview
---

# Interview process

Follow these steps to the dot --

* <mark style="color:red;">**Step #1 Ask Questions**</mark>&#x20;
  * Ask questions: get complete details about edge cases and practical scenarios
  * Functional requirements: Based on questions list down functional requirements
  * Non-Functional requirements: Based on questions list down non functional requirements like regulatory/ security contraints
  * Back of the Envelope calculations: Calculate the memory, bandwidth and compute usage, add growth projections
  * Identify assumptions and constraints explicitly:budget, latency, SLA etc<br>
* <mark style="color:red;">**Step #2 Propose a high level solution and get a buy in**</mark>
  * API design
  * High level design
  * Algorithms
  * Data model
  * Trade-off table: briefly compare alternative approaches like SQL vs nosql, monolith vs micro-services to show options
  * often the complete system will have multiple micro-service like (payment, notification, feed, upload, download etc)<br>
* <mark style="color:red;">**Step #3 Design deep dive \[ this would go for a micro-service]**</mark>
  * Scale the Database
  * Caching
  * Region and reliability zones
  * Filter results by business types
  * security and privacy
  * observability: Logging, metrics, distributed tracing, altering
  * Data lifecycle&#x20;
  * Final artitecture digram of the microservice<br>
* <mark style="color:red;">**Step $4 Wrap UP**</mark>
  * Discuss on future improvements,  requirements and how to tackle them
  * Risk analysis: Single point of faliure, mitigation plans

***

Once we have completed the step #2 High level solution each micro-service would have these 4 thing

* database and caching&#x20;
* scaling and fault tolerant
* async processing (delegation)
* communication&#x20;

After we have given step 2 interviewer would ask to go deep into one of the microservice, where we will discuss on above 4 criterias



| Phase             | Goal                                 | Key Outputs                                                                  |
| ----------------- | ------------------------------------ | ---------------------------------------------------------------------------- |
| Clarify & Scope   | Understand the _why_ and constraints | Requirements doc, assumptions list, rough capacity estimates                 |
| High-Level Design | Present a coherent _what_            | API contracts, component diagram, key trade-offs                             |
| Deep Dive         | Show you can build the _how_         | Detailed data model, scaling & caching plan, observability & security layers |
| Wrap-Up           | Show forward thinking                | Risks, future growth plan, cost & operational strategy                       |



## Step #1 Ask questions



When the interviewer gives the problem, mentally run this sequence:

1. Who/Why/Where – users, regions, goals.
2. What – core features and flows.
3. How Well – performance, scale, availability, security.
4. How Big – back-of-the-envelope numbers.



* **Ask questions** (typical questions that can be asked)
  * Goal & Use Cases
    * What core user problem are we solving?
    * Who are the primary users (end users, internal teams, 3rd parties)?
    * What are the must-have vs. nice-to-have features for MVP?
  * Actors & Interfaces
    * How do users or services interact (web, mobile app, API, IoT device)?
    * Are there 3rd-party integrations (payments, messaging, maps)?
  * Constraints & Assumptions
    * Any budgetary, regulatory, or time constraints?
    * Expected launch regions/countries (affects data residency, latency)?
    * Any legacy systems to integrate with or replace?
  * Failure Scenarios / Edge Cases
    * What happens if a request times out, a region goes down, or a dependency fails?
    * Are there seasonal spikes (sales, holidays)?
  * Future Outlook
    * Planned growth or new features in 1–3 years?
    * Any anticipated shifts (multi-cloud, more geographies)?

***





* **Functional requirements**: (Tip: Rephrase requirements as user stories (e.g., “As a rider I can track my driver in real time”) so interviewers see you think from the user’s perspective.
  * Core Features
    * What CRUD operations or primary actions must be supported?
      * Are real-time updates required (chat, live scores) or eventual updates fine?
  * Workflows
    * Typical end-to-end flows (signup → purchase → notification).
      * Do we need support for bulk operations or batch processing?
  * Business Rules
    * Validation logic, quotas, permissions.
      * Are there paid tiers, rate limits, or feature flags?
  * APIs & Interfaces
    * Should the system expose public APIs? REST or GraphQL?
    * Any webhooks or callback mechanisms?
  * Edge Cases
    * Offline mode, retries, idempotency for duplicate requests.

***





*   **NoN Functional requirements**: (Angle: Identify qualities the system must exhibit under real-world conditions.)



    * Performance & Latency
      * Target response time (e.g., <200 ms for reads)?
      * Throughput (requests/sec, events/sec)?
    * Scalability
      * Expected daily active users now vs. 1 year.
      * Traffic pattern (steady, diurnal spikes, sudden bursts).
    * Availability & Reliability
      * SLA/uptime goal (99.9%? 99.99%?).
        * RTO/RPO for disaster recovery.
      * Consistency & Durability
        * Strong vs. eventual consistency?
        * Data loss tolerance?
      * Security & Compliance
        * Authentication (OAuth, SSO), authorization (RBAC/ABAC).
        * Regulatory needs: GDPR, HIPAA, PCI-DSS.
      * Maintainability & Observability
        * Logging, monitoring, alerting requirements.
        * Deployment cadence—blue/green, canary?
      * Cost & Operations
        * Cloud budget limits?
        * Preference for managed services vs. self-hosted?

***



*   **Back of the envelope calculations** — (Angle: Show that you can estimate scale and resources quickly—don’t aim for perfect math, just an _order of magnitude_.)



    * Traffic Estimates
      * Daily active users (DAU) or monthly active users (MAU).
      * Requests per user per day.
    * Data Volume
      * Size of each object/record (e.g., 1 KB chat message, 2 MB image).
      * Read/write ratios.
    * Bandwidth
      * Peak QPS × payload size → outbound/inbound GB per second.
    * Storage
      * Daily data growth × retention period → total storage (hot vs. cold).
      * Replication factor (e.g., 3× for durability).
    * Compute
      * Approx CPU/RAM per request (if known) → # of app servers.
    * Cost Ballpark
      * Cloud egress charges, storage per TB/month, instance pricing.
    * DAU: 5 M\
      Requests/user/day: 20 → 100 M req/day ≈ 1.16k req/sec\
      Payload size: 2 KB → \~2.3 GB/day network out\
      Replication: 3x → 7 GB/day storage growth

***



## Questions&#x20;



Easy --

1\. How to Design URL Shortener like TinyURL \[[solution](https://bit.ly/3dZoQ2G)]\
2\. How to Design Text Storage Service like Pastebin? \[[solution](https://www.youtube.com/watch?v=9wAj-5IMdyU)]\
3\. Design Content Delivery Network (CDN) ? \[[solution](https://bit.ly/3dZoQ2G)]\
4\. Design Parking Garage \[[solution](https://bit.ly/3eMUosX)]\
5\. Design Vending Machine \[[solution](https://javarevisited.blogspot.com/2016/06/design-vending-machine-in-java.html)]\
6\. How to Design Distributed Key-Value Store\
7\. Design Distributed Cache\
8\. Design Distributed Job Scheduler\
9\. How to Design Authentication System\
10\. How to Design Unified Payments Interface (UPI)



medium

11\. Design Instagram \[[solution](https://bit.ly/3BqamCL)]\
12\. How to Design Tinder\
13\. Design WhatsApp ([solution](https://bit.ly/3SbA9Eu))\
14\. How to Design Facebook\
15\. Design Twitter\
16\. Design Reddit\
17\. Design Netflix \[[solution](https://bit.ly/3bbNnAN)]\
18\. Design Youtube \[[solution](https://bit.ly/3bbNnAN)]\
19\. Design Google Search\
20\. Design E-commerce Store like Amazon\
21\. Design Spotify\
22\. Design TikTok\
23\. Design Shopify\
24\. Design Airbnb\
25\. Design Autocomplete for Search Engines\
26\. Design Rate Limiter\
27\. Design Distributed Message Queue like Kafka\
28\. Design Flight Booking System\
29\. Design Online Code Editor\
30\. Design Stock Exchange System\
31\. Design an Analytics Platform (Metrics & Logging)\
32\. Design Notification Service\
33\. Design Payment System



hard

34. How to Design Location Based Service like Yelp
35. Design Uber
36. Design Food Delivery App like Doordash
37. Design Google Docs
38. How to Design Google Maps
39. Design Zoom
40. How to Design File Sharing System like Dropbox
41. How to Design Ticket Booking System like BookMyShow
42. Design Distributed Web Crawler
43. How to Design Code Deployment System
44. Design Distributed Cloud Storage like S3
45. How to Design Distributed Locking Service




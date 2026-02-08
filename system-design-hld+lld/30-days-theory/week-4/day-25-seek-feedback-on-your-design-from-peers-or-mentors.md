# Day 25: Seek Feedback on Your Design from Peers or Mentors



## 1. **Objective of Feedback**

The goal for Day 25 is to present your system design to peers or mentors and gather constructive feedback. This stage is crucial for identifying potential blind spots, improving your design, and ensuring that it aligns with best practices in software engineering. Engaging others in a review also allows you to learn from their experiences and incorporate diverse perspectives into your design.

***

## 2. **Preparation for the Review**

Before presenting your system, prepare all necessary materials so the feedback session is focused and productive:

* **Design Artifacts:**
  * **System Architecture Diagram:** Ensure your high-level design diagram is clear and reflects the components of your system (e.g., services, databases, caching, load balancers, etc.).
  * **API Documentation:** Prepare concise documentation of your core APIs, including endpoints, request/response formats, and error handling.
  * **Data Model:** Create a simplified schema of your database, showing the most important tables and their relationships.
  * **Trade-offs and Assumptions:** Document any major design decisions you’ve made (e.g., eventual consistency, caching strategies, sharding choices) and the reasoning behind them.
  * **Key Metrics and Performance Targets:** Share your scalability and performance goals (e.g., response time < 100ms, capable of handling 1 million users).
* **Areas for Specific Feedback:**
  * Identify areas where you're unsure or need guidance (e.g., database scaling, security, microservices communication).
  * Prepare specific questions to guide the feedback session (e.g., "Do you think my caching strategy is effective for the post feed?", "Is the load-balancing solution appropriate for the projected traffic?").

***

## 3. **Presenting Your Design**

When presenting your design, follow a structured approach to ensure clarity and ease of understanding. Here's a breakdown of what to cover:

1. **Introduction:**
   * Provide a brief overview of the system you are designing (e.g., a social media platform).
   * Explain the main goals of the project, including the primary features (e.g., user profiles, post creation, news feed, messaging).
2. **System Architecture:**
   * Walk through the high-level architecture and explain how different components interact.
   * Emphasize key services (e.g., User Service, Post Service, Feed Service, Notification Service) and their roles in the system.
   * Discuss the communication patterns between services (e.g., synchronous vs. asynchronous).
3. **API Design:**
   * Present the key APIs you have designed, such as those for user registration, post creation, and feed retrieval.
   * Discuss any API optimization you’ve done (e.g., pagination, lazy loading, batch requests).
4. **Database and Storage:**
   * Explain your database design, including any sharding or denormalization decisions.
   * Highlight the indexing strategies used to optimize read and write operations.
5. **Caching and Performance:**
   * Present the caching strategies you’ve implemented (e.g., caching user profiles, posts, and feeds).
   * Discuss how you are handling performance bottlenecks and ensuring low latency.
6. **Scalability and Load Balancing:**
   * Describe how your system scales (both horizontally and vertically).
   * Explain your approach to load balancing (e.g., Nginx, AWS Elastic Load Balancer) and distributing traffic across services.
7. **Failure Recovery and Fault Tolerance:**
   * Present how your system handles failures (e.g., retries, circuit breakers, graceful degradation).
   * Discuss backup mechanisms for critical data and services.
8. **Security:**
   * Outline the security measures in place, such as authentication (OAuth 2.0, JWT), data encryption, and API rate limiting.

***

## 4. **Areas for Feedback**

During the feedback session, actively ask for input on specific parts of your design. Common areas where feedback can be valuable include:

* **Service Decomposition:**
  * Are the services sufficiently decoupled? Should any service be split further into microservices?
  * Is any service taking on too many responsibilities, violating the **Single Responsibility Principle** (SRP)?
* **Database Design and Scaling:**
  * Have you made the right decision between **SQL** and **NoSQL** for your data? Would there be a better storage option for certain types of data (e.g., graph databases for relationships)?
  * Ask about alternative database sharding and replication strategies. Is there a better way to partition your data?
* **Caching:**
  * Is your caching strategy optimal, or should certain data be cached more/less aggressively?
  * What are the potential pitfalls of the current cache invalidation policy?
* **System Bottlenecks:**
  * Are there any parts of your system that might become bottlenecks as user traffic grows?
  * Can the system handle the expected load, or will additional scaling measures be necessary?
* **Consistency vs. Availability:**
  * Are the trade-offs between consistency and availability balanced? Should you lean more towards strong consistency in certain areas (e.g., critical financial transactions) or accept eventual consistency (e.g., notifications)?
* **Security:**
  * Ask for a review of your authentication and authorization mechanisms.
  * Are there potential vulnerabilities you may have missed (e.g., input validation issues, weak encryption)?
* **Performance Optimizations:**
  * Are there any additional performance optimizations that you haven’t considered (e.g., query optimization, using specialized databases like Elasticsearch for searches)?
* **Monitoring and Logging:**
  * Does your monitoring setup provide sufficient coverage of critical metrics?
  * Are there additional things you should be logging (e.g., user activity, error rates) to catch potential issues early?

***

## 5. **Incorporating Feedback**

Once you’ve gathered feedback, follow these steps to incorporate the insights into your design:

* **Prioritize Feedback:**
  * Not all feedback will be immediately actionable or relevant. Prioritize feedback that aligns with your system’s scalability, reliability, and security goals.
  * Categorize the feedback into **urgent fixes** (e.g., design flaws or security vulnerabilities) and **long-term improvements** (e.g., optimization suggestions).
* **Make Iterative Changes:**
  * Rather than implementing all changes at once, make iterative updates. Focus on one area of improvement (e.g., optimizing database queries) before moving to the next.
* **Document Revisions:**
  * Keep track of all changes made to your design based on the feedback. Update your system architecture diagram, API documentation, or data model to reflect the changes.

***

## 6. **Example Feedback Questions**

To help guide the feedback session, you can use these example questions:

* **Service Boundaries:** “Do you think the current boundaries between services are appropriate, or should some services be further split into smaller microservices?”
* **Database Design:** “Is the current database schema optimal for the types of queries this system will handle? Are there any potential scaling issues with the current design?”
* **Caching Strategy:** “Is my caching strategy sound, or are there areas where it might cause data inconsistency or stale reads?”
* **Security Measures:** “Do you see any security vulnerabilities in how I’ve implemented authentication and authorization?”
* **Handling Traffic Spikes:** “Can the system scale horizontally to handle unexpected traffic spikes, or should I introduce additional measures like autoscaling?”

***

## 7. **Post-Feedback Reflection**

After the feedback session:

* **Reflect on the Key Takeaways:** What were the most significant points raised? Were there any critical flaws or areas where your assumptions were challenged?
* **Plan for Refinement:** Based on the feedback, create a plan to refine your design on Day 26, where you will explore advanced topics and make necessary changes.
* **Thank Your Reviewers:** Acknowledge the time and effort your peers or mentors invested in giving you feedback. If they raised a particularly important point, let them know how their feedback impacted your design process.

***

## Next Steps for Day 25:

* **Gather Feedback:** Present your design, gather comprehensive feedback, and clarify any questions or doubts.
* **Review Feedback:** Evaluate the feedback and prioritize the most critical areas that need improvement.
* **Document Adjustments:** Keep track of the feedback and the adjustments that need to be made. Prepare for refining your system in the upcoming days.

By the end of Day 25, you should have **a clear understanding of areas for improvement** in your system design and **a roadmap for refinement** based on peer or mentor feedback.

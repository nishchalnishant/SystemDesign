# Day 3: Study Load Balancing Techniques

## **What is Load Balancing?**

Load balancing is a technique used in distributed systems to evenly distribute incoming network traffic across multiple servers. The goal is to ensure that no single server becomes overwhelmed while others remain underutilized. This improves the system’s overall reliability, availability, and responsiveness.

Load balancers play a critical role in achieving horizontal scaling, ensuring that requests are efficiently distributed across different servers.

***

## **Key Benefits of Load Balancing**

1. **High Availability and Reliability:**
   * By distributing traffic among several servers, load balancers ensure that even if one server fails, traffic can be rerouted to others.
2. **Scalability:**
   * Load balancing makes it easier to add more servers to handle increased traffic, promoting horizontal scalability.
3. **Improved Performance:**
   * Load balancers can direct traffic to the server that is closest to the user geographically (geo-load balancing) or the least busy (load-based balancing), reducing latency and improving user experience.
4. **Fault Tolerance:**
   * In the case of server failure, load balancers can automatically reroute traffic to healthy servers without downtime, improving system fault tolerance.

***

## **Types of Load Balancing**

1. **Hardware Load Balancers:**
   * Dedicated physical devices used in data centers for load balancing.
   * They are expensive but offer high performance and security.
   * Examples: F5 Networks, Citrix ADC.
2. **Software Load Balancers:**
   * Run on commodity hardware or virtual machines (VMs) to distribute traffic.
   * More flexible and scalable than hardware load balancers.
   * Examples: HAProxy, NGINX, Traefik.
3. **Cloud-based Load Balancers:**
   * Load balancing services provided by cloud platforms such as AWS, Google Cloud, and Microsoft Azure.
   * These are fully managed, easy to deploy, and scale automatically with demand.
   * Examples: AWS Elastic Load Balancing (ELB), Google Cloud Load Balancing, Azure Load Balancer.

***

## **Load Balancing Algorithms**

Different load balancing algorithms dictate how requests are distributed across servers. Here are the common algorithms:

1. **Round Robin:**
   * Requests are distributed sequentially to each server in a circular order.
   * Simple and easy to implement, but doesn't account for server load or performance.
2. **Least Connections:**
   * Directs traffic to the server with the fewest active connections.
   * Useful when there is a lot of variation in request processing times across servers.
3. **Least Response Time:**
   * Sends traffic to the server with the fastest response time, ensuring that the most efficient server handles requests.
   * Combines server load and response time for decision-making.
4. **IP Hash:**
   * The client’s IP address is used to determine which server will handle the request.
   * This ensures that a particular client is always routed to the same server (session persistence), useful for maintaining session data locally on servers.
5. **Weighted Round Robin:**
   * Similar to Round Robin but assigns more requests to servers with higher capacity or better performance.
   * Useful when servers have different processing power.
6. **Geolocation-based:**
   * Directs traffic to the server geographically closest to the client.
   * This reduces latency, especially in globally distributed systems.
7. **Random:**
   * Assigns incoming requests to random servers.
   * Simple and can work well when all servers have equal load and performance capacity.

***

## **Components of a Load Balancer**

1. **Frontend:**
   * This is the part of the load balancer that receives incoming client requests and decides how to route them.
2. **Backend Servers:**
   * These are the actual servers or instances that handle requests after the load balancer routes traffic to them.
3. **Health Checks:**
   * Load balancers regularly check the status of backend servers to determine if they are healthy and available to handle requests.
   * If a server is down, the load balancer redirects traffic to another server.
4. **Session Persistence (Sticky Sessions):**
   * This feature ensures that all requests from a particular client are directed to the same server.
   * It’s useful for maintaining state in applications where session data is stored locally on servers.

***

## **Types of Load Balancers by Layer**

Load balancers operate at different layers of the OSI model, primarily Layer 4 and Layer 7.

1. **Layer 4 (Transport Layer) Load Balancers:**
   * Operates at the transport layer, directing traffic based on IP addresses and TCP/UDP ports.
   * They don't inspect the actual content of the network packets, making them faster but less flexible.
2. **Layer 7 (Application Layer) Load Balancers:**
   * Operates at the application layer, directing traffic based on more detailed information like HTTP headers, URL, or cookies.
   * More intelligent than Layer 4, allowing for advanced features like content-based routing (e.g., routing video requests to one server and image requests to another).

**Example of Layer 4 Load Balancer:**

* AWS Network Load Balancer (NLB) — operates at Layer 4 and can handle millions of requests per second.

**Example of Layer 7 Load Balancer:**

* AWS Application Load Balancer (ALB) — operates at Layer 7 and supports routing based on HTTP, HTTPS, or WebSocket protocols.

***

## **Advanced Load Balancing Techniques**

1. **SSL Termination:**
   * The load balancer handles SSL encryption and decryption, offloading this computationally expensive task from the backend servers.
   * This improves the performance of backend servers by reducing their load.
2. **Global Server Load Balancing (GSLB):**
   * Distributes traffic across servers located in different geographical regions.
   * Useful for global applications to ensure low latency and high availability across regions.
   * Cloud providers often offer managed services that include GSLB.
3. **Auto-scaling Integration:**
   * Load balancers can work in conjunction with auto-scaling groups to dynamically adjust the number of backend servers based on demand.
   * This ensures that enough resources are available during traffic spikes and that excess resources are released when demand decreases.

***

## **Load Balancing in the Cloud**

Cloud platforms such as AWS, Google Cloud, and Microsoft Azure offer managed load balancing solutions that integrate seamlessly with other cloud services like auto-scaling, security groups, and content delivery networks (CDNs).

1. **AWS Elastic Load Balancer (ELB):**
   * Automatically distributes incoming traffic across multiple EC2 instances, containers, or IP addresses.
   * Supports Layer 4 (Network Load Balancer), Layer 7 (Application Load Balancer), and Gateway Load Balancer.
2. **Google Cloud Load Balancing:**
   * Global load balancing service that can route traffic based on geo-location, latency, and other parameters.
   * Supports auto-scaling and SSL offloading.
3. **Azure Load Balancer:**
   * Offers both public and internal load balancing to distribute traffic across virtual machines and containers.

***

## **Load Balancing Use Cases**

1. **Web Applications:**
   * Distribute HTTP or HTTPS requests across multiple web servers, ensuring high availability and low latency.
2. **Microservices Architecture:**
   * Load balancers direct traffic between services in a microservices-based architecture, ensuring each service can scale independently.
3. **Database Load Balancing:**
   * Distribute database queries across read replicas to improve performance and availability.
4. **Content Delivery Networks (CDNs):**
   * Use load balancing to route requests to the closest edge server, reducing latency for users located far from the origin server.

***

## **Challenges with Load Balancing**

1. **Session Persistence:**
   * When session data is stored locally on a server, load balancers need to ensure that subsequent requests from the same client are routed to the same server (sticky sessions). This can limit flexibility in distributing traffic.
2. **Consistency in Distributed Systems:**
   * Load balancing distributed databases can introduce consistency challenges (eventual vs. strong consistency).
3. **Latency and Overhead:**
   * Although load balancers improve overall system performance, they add a small amount of latency since each request must pass through an additional layer.

***


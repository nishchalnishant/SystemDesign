> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How modern teams deploy, scale, and manage distributed systems — from containers (Docker) to orchestration (Kubernetes).
>
> **Key topics:**
> - Why VMs were replaced by containers
> - Docker: what a container actually is
> - Kubernetes architecture: the control plane vs worker nodes
> - Core K8s concepts: Pods, Deployments, Services, Ingress
> - Horizontal Pod Autoscaling (HPA) — how systems auto-scale under load
> - Production failure scenarios and how K8s handles them
>
> **Key takeaway:** In a system design interview, when asked "how do you deploy and scale this?", your answer should mention containerization, horizontal scaling via Kubernetes HPA, and health checks/rolling deployments.

---
module: 04-advanced-topics/01-distributed-architecture
status: unread
tags: [kubernetes, docker, containers, orchestration, scaling, deployment]
---

# Kubernetes & Container Orchestration — System Design Guide

## Why Should I Care?

You've designed a perfect distributed system. You've picked the right databases, caches, and message queues. Then the interviewer asks:

> "How do you actually deploy this? How does it scale when traffic spikes? What happens when a server crashes at 3 AM?"

This is where **containers** and **Kubernetes** come in. Every major tech company — Google, Meta, Uber, Netflix — runs their backend on containerized infrastructure managed by an orchestration platform.

---

## Part 1: Containers (Docker) — "Standardized Shipping Boxes"

**Analogy:** Before shipping containers were invented, loading cargo onto ships was chaos — every item was different and required custom handling. After standardized metal containers were introduced, any container could go on any ship, truck, or train without modification.

Docker does the same thing for software. It packages your application and **everything it needs** (code, runtime, libraries, environment variables) into a single standardized unit called a **container image**.

### VM vs Container

```
┌────────────────────────┐   ┌──────────────────────────────────────┐
│      Virtual Machine   │   │            Containers                │
├────────────────────────┤   ├──────────┬──────────┬───────────────┤
│  App A   │  App B     │   │  App A   │  App B   │   App C       │
│  OS Copy │  OS Copy   │   ├──────────┴──────────┴───────────────┤
│  (2 GB)  │  (2 GB)    │   │         Container Runtime (Docker)   │
├──────────┴────────────┤   ├──────────────────────────────────────┤
│     Hypervisor         │   │              Host OS                 │
└────────────────────────┘   └──────────────────────────────────────┘
  Each VM = 2-4 GB RAM         Each container = ~100 MB RAM
  Slow to boot (minutes)       Fast to start (milliseconds)
```

**Key insight:** Containers share the host OS kernel. VMs each carry their own OS. This makes containers 10-20x more resource-efficient.

### A Basic Dockerfile

```dockerfile
FROM python:3.11-slim         # Start from a base image
WORKDIR /app                  # Set working directory
COPY requirements.txt .
RUN pip install -r requirements.txt  # Install dependencies
COPY . .                      # Copy app code
EXPOSE 8000                   # Expose port
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

Build and run:
```bash
docker build -t my-api:v1.0 .
docker run -p 8000:8000 my-api:v1.0
```

---

## Part 2: The Problem with Bare Containers

Running containers with Docker alone quickly becomes unmanageable at scale:

- **How do you run 100 containers across 20 servers?**
- **What happens when a container crashes?** Who restarts it?
- **How do you update your app without downtime?**
- **How do you route traffic to healthy containers only?**
- **How do you scale up when CPU spikes?**

This is exactly what **Kubernetes (K8s)** solves.

---

## Part 3: Kubernetes — "The Container Air Traffic Controller"

**Analogy:** Imagine an airport. Planes (containers) need to land, take off, and be routed to the right gates. Air traffic controllers (Kubernetes) manage all of this automatically — no human directs each individual plane. They just set policies: "Always have 3 planes at Gate A. If one leaves, bring another."

Kubernetes is a **container orchestration platform** that:
- Decides *where* to run containers across a cluster of servers.
- Ensures the *desired number* of containers are always running.
- Replaces failed containers automatically.
- Routes traffic to healthy containers.
- Scales containers up or down based on load.

### The Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        CONTROL PLANE                             │
│  ┌──────────────┐  ┌──────────┐  ┌────────────────────────────┐ │
│  │ API Server   │  │Scheduler │  │  Controller Manager         │ │
│  │ (brain)      │  │(placement│  │  (watches desired state)   │ │
│  └──────────────┘  └──────────┘  └────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                  etcd (cluster state DB)                    │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘

┌────────────────────────┐    ┌────────────────────────┐
│      WORKER NODE 1     │    │      WORKER NODE 2     │
│  ┌──────────────────┐  │    │  ┌──────────────────┐  │
│  │  Pod (App v1.0)  │  │    │  │  Pod (App v1.0)  │  │
│  │  Pod (App v1.0)  │  │    │  │  Pod (Cache)     │  │
│  └──────────────────┘  │    │  └──────────────────┘  │
│  kubelet (node agent)  │    │  kubelet (node agent)  │
└────────────────────────┘    └────────────────────────┘
```

### Control Plane Components

| Component | Role | Analogy |
|---|---|---|
| **API Server** | Entry point for all commands | The reception desk |
| **Scheduler** | Decides which node runs each pod | The seating host |
| **Controller Manager** | Watches state, drives toward desired state | The floor manager |
| **etcd** | Key-value store for all cluster state | The notebook of record |

---

## Part 4: Core Kubernetes Concepts

### 4.1 Pod — The Smallest Unit

A **Pod** is a wrapper around one or more containers that are co-located and share the same network namespace. Think of it as a single unit of deployment.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: api-server
spec:
  containers:
  - name: api
    image: my-api:v1.0
    ports:
    - containerPort: 8000
    resources:
      requests:
        memory: "128Mi"
        cpu: "250m"     # 0.25 CPU cores
      limits:
        memory: "256Mi"
        cpu: "500m"
```

> [!IMPORTANT]
> **Pods are ephemeral.** They are born and die frequently. Never rely on a Pod's IP address directly. Use a **Service** (below) to communicate between pods.

### 4.2 Deployment — Maintaining Replicas

A **Deployment** tells Kubernetes: "I want exactly N copies of this Pod running at all times."

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-deployment
spec:
  replicas: 3           # I want 3 pods always running
  selector:
    matchLabels:
      app: my-api
  template:
    spec:
      containers:
      - name: api
        image: my-api:v1.0
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1   # Never kill more than 1 pod at a time
      maxSurge: 1         # Can temporarily have 1 extra pod during update
```

**Rolling update behavior:**
1. Kubernetes starts a new pod with `v2.0`.
2. Waits for it to become healthy (passes health checks).
3. Terminates one old `v1.0` pod.
4. Repeats until all pods are updated.
5. Zero downtime throughout.

### 4.3 Service — Stable Networking

Since Pod IPs change constantly, a **Service** provides a stable virtual IP and DNS name that load-balances across all matching pods.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-service
spec:
  selector:
    app: my-api      # Route to all pods labeled app=my-api
  ports:
  - port: 80
    targetPort: 8000
  type: ClusterIP    # Internal-only (use LoadBalancer for external)
```

**Service types:**
- `ClusterIP`: Internal cluster traffic only.
- `NodePort`: Expose on a port on each node (basic).
- `LoadBalancer`: Provision a cloud load balancer (AWS ALB, GCP LB).

### 4.4 Ingress — HTTP Routing

An **Ingress** is an HTTP router that sits in front of services. It routes external traffic based on host/path rules:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-ingress
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /users
        backend:
          service:
            name: user-service
            port:
              number: 80
      - path: /orders
        backend:
          service:
            name: order-service
            port:
              number: 80
```

This is essentially your API Gateway / reverse proxy in a K8s context.

---

## Part 5: Auto-Scaling — The Key Interview Topic

### Horizontal Pod Autoscaler (HPA)

HPA automatically scales the number of pod replicas based on CPU, memory, or custom metrics.

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-deployment
  minReplicas: 2      # Never go below 2
  maxReplicas: 50     # Never go above 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70   # Scale up when avg CPU > 70%
```

**How HPA responds to a traffic spike:**

```
Time  Pods  CPU%
 0s     2   15%   (normal traffic)
10s     2   85%   (spike begins — exceeds 70% target)
30s     4   45%   (HPA added 2 pods, CPU dropped)
60s     6   32%   (traffic still high, HPA adds more)
 5m     2   18%   (traffic subsides, HPA scales down — with cooldown)
```

### Cluster Autoscaler

HPA scales pods. The **Cluster Autoscaler** scales the underlying *nodes* (VMs):
- If HPA wants 50 pods but only 10 fit on existing nodes → Cluster Autoscaler adds new nodes.
- If nodes are underutilized → it removes them to save cost.

---

## Part 6: Failure Scenarios (What Interviewers Probe)

### What happens when a Pod crashes?

The **Controller Manager** continuously compares desired state (3 replicas) vs actual state (2 running). It immediately creates a new Pod to restore desired count. Typical recovery time: 5-30 seconds.

### What happens when a Node dies?

1. `kubelet` on the dead node stops sending heartbeats.
2. After a timeout (~40s), the Controller Manager marks the node as `NotReady`.
3. All pods on that node are rescheduled to healthy nodes.
4. The Service stops routing traffic to dead pods.

### What happens during a bad deployment?

If `v2.0` pods crash immediately on startup, the rolling update **stalls**. The old `v1.0` pods continue serving traffic. Kubernetes never removes them because the new pods never become healthy. You can roll back with:

```bash
kubectl rollout undo deployment/api-deployment
```

---

## Part 7: System Design Interview — How to Use This

When designing any system, after drawing the architecture diagram, mention:

1. **"Each service is containerized and deployed as a Kubernetes Deployment."**
2. **"We set min/max replicas and configure HPA to scale based on CPU/RPS."**
3. **"Rolling deployments ensure zero-downtime releases."**
4. **"If a pod crashes, the Deployment controller restarts it automatically within ~10 seconds."**
5. **"Services and Ingress handle internal and external load balancing."**

### Common Interview Questions

**Q: How does your Uber ride-sharing design scale from 1M to 100M users?**
A: Each microservice (location service, matching service, payment service) is a separate Kubernetes Deployment. HPA scales each independently based on load. The location service might scale to 200 pods during rush hour while the payment service stays at 10.

**Q: How do you do a zero-downtime deployment?**
A: Kubernetes rolling deployments — new pods come up, health checks pass, old pods terminate. The Service only routes to healthy pods.

**Q: What's the difference between horizontal and vertical scaling in K8s?**
A: Horizontal = add more pods (HPA). Vertical = give existing pods more CPU/RAM (Vertical Pod Autoscaler, VPA). Horizontal is preferred because it's faster and more resilient.

---

> [!TIP]
> **Mental Model**
> - **Docker** = a standardized box for your app
> - **Pod** = a box on a shelf (ephemeral, has an address)
> - **Deployment** = a rule that says "keep 3 boxes on shelf at all times"
> - **Service** = a stable label on the shelf (doesn't change even when boxes swap)
> - **Ingress** = the store's front door that directs customers to the right shelf
> - **HPA** = the manager who adds/removes shelves based on demand

# Kubernetes & Docker & Containers

> **Source**: Videos #8, #12, #23, #43, #78, #88 from the playlist
> - But What Is Cloud Native Really All About?
> - Kubernetes Explained in 6 Minutes
> - Big Misconceptions about Bare Metal, Virtual Machines, and Containers
> - Is Docker Still Relevant?
> - Why is Kubernetes Popular | What is Kubernetes?
> - System Design: Why Is Docker Important?

---

## Bare Metal vs VMs vs Containers

| Feature | Bare Metal | Virtual Machine | Container |
|---|---|---|---|
| **Isolation** | Physical machine | Hypervisor-level | OS-level (process) |
| **Startup Time** | Minutes | Seconds-Minutes | Milliseconds |
| **Resource Overhead** | None | ~15-20% (hypervisor) | ~1-2% |
| **Portability** | None | Good (VM images) | Excellent (Docker images) |
| **Density** | 1 app/server | ~10-20 VMs/server | ~100s containers/server |
| **Use Case** | High-performance, legacy | Multi-tenant, strong isolation | Microservices, CI/CD |

---

## Docker

### What is Docker?
- **Containerization platform**: Package app + dependencies into a portable image
- **Dockerfile** → **Image** → **Container**

### Why Docker?
1. **Consistency**: "Works on my machine" → works everywhere
2. **Isolation**: Each container has its own filesystem, network
3. **Lightweight**: Shares OS kernel, much lighter than VMs
4. **Fast startup**: Milliseconds vs minutes for VMs
5. **Reproducibility**: Same image = same environment

### Key Concepts
```
Dockerfile   → Blueprint for building an image
Image        → Read-only template with app + dependencies
Container    → Running instance of an image
Registry     → Storage for images (Docker Hub, ECR, GCR)
Volume       → Persistent storage for containers
Network      → Communication between containers
```

### Is Docker Still Relevant?
- **Yes**, but the ecosystem evolved:
  - Docker for **building images**: Still dominant
  - Docker Desktop: Has alternatives (Podman, Rancher Desktop)
  - Docker runtime: Kubernetes uses containerd/CRI-O now
  - OCI (Open Container Initiative) standardized container format

---

## Kubernetes (k8s)

### What is Kubernetes?
- **Container orchestration platform**: Manages containerized apps at scale
- Automates deployment, scaling, and management of containers
- Originally developed by Google (based on Borg)

### Why Kubernetes?
1. **Auto-scaling**: Scale pods up/down based on demand
2. **Self-healing**: Restart failed containers automatically
3. **Service discovery**: Built-in DNS for services
4. **Load balancing**: Distribute traffic across pods
5. **Rolling updates**: Zero-downtime deployments
6. **Declarative config**: Define desired state, k8s makes it happen

### Architecture

```
┌──────────────── Control Plane ────────────────┐
│  API Server │ etcd │ Scheduler │ Controller   │
└──────────────────────────────────────────────┘
        ↕                ↕               ↕
┌─── Worker Node 1 ──┐  ┌─── Worker Node 2 ──┐
│  kubelet │ kube-proxy│  │  kubelet │ kube-proxy│
│  ┌─Pod──┐ ┌─Pod──┐  │  │  ┌─Pod──┐ ┌─Pod──┐  │
│  │ C1   │ │ C2   │  │  │  │ C3   │ │ C4   │  │
│  └──────┘ └──────┘  │  │  └──────┘ └──────┘  │
└─────────────────────┘  └─────────────────────┘
```

### Key Components
- **Pod**: Smallest deployable unit (1+ containers)
- **Service**: Stable network endpoint for pods
- **Deployment**: Manages pod replicas and updates
- **Ingress**: HTTP routing from outside the cluster
- **ConfigMap/Secret**: Configuration management
- **Namespace**: Logical isolation within a cluster

---

## Cloud Native

### What Does "Cloud Native" Mean?
- Design apps specifically for cloud environments
- Key principles:
  1. **Containerized**: Package in containers
  2. **Dynamically managed**: Orchestrated (Kubernetes)
  3. **Microservices-oriented**: Loosely coupled services
  4. **Automated**: CI/CD pipelines, infrastructure-as-code

### Cloud Native Landscape
- **CNCF** (Cloud Native Computing Foundation) governs the ecosystem
- Key projects: Kubernetes, Prometheus, Envoy, Helm, Istio

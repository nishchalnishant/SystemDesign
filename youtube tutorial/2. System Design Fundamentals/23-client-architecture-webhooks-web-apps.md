# Client Architecture, Web Apps & Webhooks

> **Source**: Videos #52, #56, #80 from the playlist
> - Everything You NEED to Know About Client Architecture Patterns
> - Top 3 Things You Should Know About Webhooks!
> - Everything You NEED to KNOW About Web Applications

---

## Client Architecture Patterns

### 1. MVC (Model-View-Controller)
```
User → Controller → Model → View → User
```
- **Model**: Data and business logic
- **View**: UI/presentation
- **Controller**: Handles input, updates Model, selects View
- **Used by**: Rails, Django, Spring MVC

### 2. MVP (Model-View-Presenter)
```
User → View → Presenter → Model → Presenter → View
```
- View is passive (thin)
- Presenter handles all logic
- Easier to unit test than MVC

### 3. MVVM (Model-View-ViewModel)
```
User → View ← (data binding) → ViewModel → Model
```
- Two-way data binding between View and ViewModel
- **Used by**: React, Angular, Vue, SwiftUI
- ViewModel exposes data streams that View observes

### 4. Flux / Redux (Unidirectional)
```
Action → Dispatcher → Store → View → Action
```
- Single source of truth (store)
- Predictable state changes
- **Used by**: React/Redux

---

## Web Application Architecture

### Frontend
```
Browser → HTML/CSS/JS → Framework (React/Vue/Angular)
```

### Backend
```
API Server → Business Logic → Database
```

### Full Stack Architecture
```
Client (Browser/Mobile)
    ↓
CDN (static assets)
    ↓
Load Balancer
    ↓
Web Server / API Server
    ↓
Application Layer (business logic)
    ↓
Database + Cache
    ↓
Background Workers + Message Queue
```

### Rendering Strategies
| Strategy | Where HTML is Generated | Pros | Cons |
|---|---|---|---|
| **SSR** (Server-Side) | Server | SEO, fast first load | Higher server cost |
| **CSR** (Client-Side) | Browser (JS) | Rich interactions | Slow first load, poor SEO |
| **SSG** (Static Generation) | Build time | Fastest, cheapest | Only for static content |
| **ISR** (Incremental Static) | Build time + on-demand | Best of SSR + SSG | More complex |

---

## Webhooks

### What is a Webhook?
A webhook is an HTTP callback — when an event occurs, the source service sends an HTTP POST to your registered URL.

### How It Works
```
1. You register a webhook URL with the service
   POST /webhooks → { "url": "https://myapp.com/callback", "events": ["payment.success"] }

2. When the event occurs, service sends HTTP POST to your URL
   POST https://myapp.com/callback
   Body: { "event": "payment.success", "data": { "amount": 100 } }

3. Your server processes the event and returns 200 OK
```

### Top 3 Things to Know

#### 1. Webhook Security
- **Signature verification**: Service signs the payload; verify with shared secret
- **HTTPS only**: Never accept webhooks over HTTP
- **IP allowlisting**: Only accept from known source IPs

#### 2. Reliability
- Services typically **retry** on failure (3-5 retries with exponential backoff)
- Your endpoint must be **idempotent** (same event delivered multiple times)
- Use a **unique event ID** to deduplicate
- Return **200** quickly, process asynchronously

#### 3. Best Practices
- Respond with **200** immediately, process in background
- Store the raw payload before processing
- Implement a **dead letter queue** for failed processing
- Monitor webhook delivery rates and failures

### Webhook vs Polling vs SSE vs WebSocket

| Method | Direction | Connection | Best For |
|---|---|---|---|
| **Webhook** | Server → Client (HTTP) | Per-event | Event notifications |
| **Polling** | Client → Server | Repeated requests | Simple, infrequent updates |
| **SSE** | Server → Client | Persistent (HTTP) | One-way real-time |
| **WebSocket** | Bidirectional | Persistent | Chat, gaming, live data |

# REST API

> **Source**: [What Is REST API? Examples And How To Use It: Crash Course System Design #3](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #4

---

## What is REST?

**REST** (Representational State Transfer) is an architectural style for designing networked APIs. It uses HTTP methods to perform CRUD operations on resources.

---

## REST Principles

1. **Client-Server**: Separation of concerns
2. **Stateless**: Each request contains all info needed; no session stored on server
3. **Cacheable**: Responses must define themselves as cacheable or not
4. **Uniform Interface**: Consistent resource-based URLs
5. **Layered System**: Client can't tell if connected directly to server or intermediary
6. **Code on Demand** (optional): Server can send executable code

---

## HTTP Methods → CRUD

| Method | Operation | Idempotent | Example |
|---|---|---|---|
| `GET` | Read | Yes | `GET /users/123` |
| `POST` | Create | No | `POST /users` |
| `PUT` | Update (full) | Yes | `PUT /users/123` |
| `PATCH` | Update (partial) | No | `PATCH /users/123` |
| `DELETE` | Delete | Yes | `DELETE /users/123` |

---

## Status Codes

| Range | Category | Common Codes |
|---|---|---|
| 2xx | Success | 200 OK, 201 Created, 204 No Content |
| 3xx | Redirection | 301 Moved Permanently, 304 Not Modified |
| 4xx | Client Error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Rate Limited |
| 5xx | Server Error | 500 Internal Error, 502 Bad Gateway, 503 Service Unavailable |

---

## Best Practices

- Use **nouns** for resources: `/users` not `/getUsers`
- Use **plural** nouns: `/users` not `/user`
- Nest for relationships: `/users/123/posts`
- Use query params for filtering: `/users?role=admin&sort=name`
- Version your API: `/api/v1/users`
- Use proper status codes
- Return JSON by default

---

## REST vs Other API Styles

| Feature | REST | GraphQL | gRPC |
|---|---|---|---|
| Protocol | HTTP/1.1 | HTTP | HTTP/2 |
| Data Format | JSON | JSON | Protobuf (binary) |
| Contract | Loose (OpenAPI) | Schema (SDL) | Strict (.proto) |
| Best For | Public APIs | Complex frontends | Microservices |

# API Conventions

This document records shared HTTP behavior that should remain consistent across
business services.

## Pagination

List APIs use database pagination:

```http
GET /api/v1/products?page=1&size=20
```

- `page` starts at `1` and defaults to `1`.
- `size` must be between `1` and `100` and defaults to `20`.
- Invalid parameters return `400 application/problem+json`.
- A page beyond the available data returns `200` with an empty `content` array.

Example response:

```json
{
  "content": [],
  "page_info": {
    "number": 1,
    "size": 20,
    "number_of_elements": 0,
    "total_elements": 0,
    "total_pages": 0,
    "is_first": true,
    "is_last": true
  },
  "request_id": null
}
```

## Problem Details

HTTP errors use `application/problem+json` based on Spring `ProblemDetail` and the
Problem Details standard.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/products/purchase",
  "errors": {
    "products[0].quantity": "Quantity is mandatory"
  }
}
```

Expected status behavior:

- `400`: validation, malformed JSON, or rejected business input.
- `401`: missing or invalid authentication.
- `403`: authenticated caller without sufficient permission.
- `404`: resource not found.
- `405`: unsupported HTTP method; preserve the `Allow` header.
- `409`: conflicting state or idempotency-key reuse with a different request.
- `500`: unexpected internal error without leaking stack traces or secrets.
- `503`: unavailable service or no gateway route instance.

Business errors should evolve toward stable, machine-readable error codes so callers
do not need to parse human-readable messages.

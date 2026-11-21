# API Conventions

This document records shared HTTP behavior that should remain consistent across
business services.

## Success envelope

Accepted by the project owner on 2026-11-16: Customer, Product, Order and Payment
explicitly wrap successful response bodies with `BaseResponse.of(...)` at the
controller boundary. No automatic `ResponseBodyAdvice` is installed.

```json
{
  "data": { "id": "c1", "firstname": "Ada" },
  "request_id": null
}
```

Scalar IDs, booleans, objects and arrays all appear under `data`. Responses with
HTTP 202 and no result use `data: null`; HTTP 204 remains empty as required by its
semantics. Successful status codes and request payloads are preserved.

Internal Customer, Product and Payment endpoints use the same envelope. Feign
contracts in `proxy-module` decode it through their own `ServiceResponse<T>` wire
DTO, with no dependency on `web-module`. Order unwraps `data` in its consuming
service/gateway. OAuth token responses are third-party contracts and stay raw.
This is a response-contract change: deploy the service endpoints and their known
Feign consumers together, and update other clients to read `data`.

`request_id` belongs to the outer envelope only and uses the current MDC trace ID.
The nested `PageResponse` contains only `content` and `page_info`.

## Pagination

List APIs use database pagination:

```http
GET /api/v1/products?page=1&size=20
```

- `page` starts at `1` and defaults to `1`.
- `size` must be between `1` and `100` and defaults to `20`.
- Invalid parameters handled by the shared MVC handler return `400 application/json`
  using the error envelope documented below.
- A page beyond the available data returns `200` with an empty `data.content` array.

Example response:

```json
{
  "data": {
    "content": [],
    "page_info": {
      "number": 1,
      "size": 20,
      "number_of_elements": 0,
      "total_elements": 0,
      "total_pages": 0,
      "is_first": true,
      "is_last": true
    }
  },
  "request_id": null
}
```

## Error envelope

Customer, Product, Order and Payment use the shared `web-module` MVC handler.
The owner selected this format on 2026-11-16; see
[Exception handling design](exception-handling-design.md) for the decision,
ownership, scope and future extension paths.

```json
{
  "data": {
    "error_code": "INVALID_INPUT_DATA",
    "extra_data": {
      "message": "Request validation failed",
      "errors": {
        "products[0].quantity": "Quantity is mandatory"
      }
    }
  },
  "request_id": null
}
```

HTTP status indicates the actual failure; content type is `application/json`.
For classified 4xx errors, a service may supply its own code and public details in
`data.extra_data.details`. Otherwise the handler uses the default code for the status.
Messages for expected errors come from the classified exception. Unknown server
errors use a safe fixed message. Rejected values and debugging details are omitted.
This migration does not change responses created directly by security filters,
the gateway or infrastructure outside MVC, which can still use other formats.

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

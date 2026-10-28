# Topic 7 — Observability and SLO

**Status:** Planned  
**Depends on:** Các business flow và failure state đã ổn định

## 1. Vấn đề cần giải quyết

Log riêng lẻ không trả lời được order đã đi qua service nào, chậm ở đâu và compensation
có bị kẹt không. Observability kết hợp trace, metric và structured log để trả lời câu hỏi
vận hành từ góc nhìn người dùng và business flow.

## 2. Kiến thức học được

- Distributed tracing và context propagation.
- RED metrics, business metrics và consumer lag.
- SLI/SLO, alert theo symptom.
- Correlation ID và structured logging.

## 3. Tính năng cần xây dựng

- OpenTelemetry trace xuyên HTTP và Kafka.
- Structured JSON log có `traceId`, `orderId`, `reservationId`, `eventId`.
- Prometheus scrape endpoint.
- Grafana dashboard cho order flow.
- Alert và runbook cho failure chính.

## 4. Metric tối thiểu

```text
http.server.request.duration
order.created.total
order.confirmed.total
order.cancelled.total
order.saga.duration
inventory.reservation.active
inventory.reservation.expired.total
payment.failed.total
compensation.pending
outbox.pending
outbox.oldest.age
kafka.consumer.lag
kafka.dlq.depth
```

Không dùng `orderId` làm metric label vì cardinality quá cao; ID chỉ nằm trong trace/log.

## 5. SLI/SLO ban đầu

- 99% create-order request nhận kết quả chấp nhận/xử lý trong 1 giây mỗi 30 ngày.
- 99.9% confirmed payment dẫn tới terminal order state trong 5 phút.
- 99.9% outbox event được publish trong 60 giây.
- Không compensation nào ở pending quá 15 phút mà không alert.

Các con số là baseline học tập và phải điều chỉnh sau load test.

## 6. Non-Functional Requirements

- **Trace continuity:** Trace context truyền qua HTTP và Kafka.
- **Low overhead:** Sampling/export không làm critical path mất ổn định.
- **Cardinality control:** Không dùng ID người dùng/order làm metric label.
- **Privacy:** Log không chứa token, card data, password hoặc secret.
- **Actionability:** Alert phải gắn với symptom và có runbook.
- **Retention:** Log/trace retention phù hợp môi trường và chi phí.

## 7. Test strategy

- Integration test xác nhận trace headers được propagate.
- Kiểm tra event có trace metadata.
- Dashboard smoke test với failure giả lập.
- Alert test cho outbox lag, DLQ depth và compensation pending.
- Log review chứng minh không lộ secret/PII nhạy cảm.

## 8. Acceptance criteria

- [ ] Từ `orderId` tìm được trace và toàn bộ log liên quan.
- [ ] Dashboard hiển thị success rate, latency và failure stage.
- [ ] Alert có threshold, owner và runbook.
- [ ] Metric không có high-cardinality label.
- [ ] Có ít nhất một failure drill được quan sát end-to-end.


# Topic N — Tên topic

**Status:** Proposed | Planned | In progress | Implemented  
**Owner:** Team hoặc người phụ trách  
**Depends on:** Link tới topic prerequisite

> Xóa các hướng dẫn trong template sau khi hoàn thiện tài liệu.

## 1. Vấn đề cần giải quyết

Mô tả behavior hiện tại, điều gì có thể sai và ảnh hưởng tới người dùng hoặc hệ thống.
Người đọc phải hiểu lý do cần topic này mà chưa cần biết implementation.

## 2. Mục tiêu học tập

- Concept hoặc pattern sẽ học.
- Failure mode sẽ quan sát.
- Trade-off cần có khả năng giải thích sau khi hoàn thành.

## 3. Scope và non-goals

### Trong phạm vi

- Business scenario tối thiểu.
- Service/component được thay đổi.

### Ngoài phạm vi

- Những feature cố ý chưa làm.
- Công nghệ chưa cần đưa vào.

## 4. Current state và gap

Mô tả code/flow hiện tại, behavior còn thiếu và bằng chứng như test hoặc metric nếu có.

## 5. Thiết kế đề xuất

Thêm flow diagram nhỏ nếu có từ ba bước phụ thuộc trở lên. Ghi rõ service ownership,
transaction boundary và nơi lưu source of truth.

## 6. Tính năng cần xây dựng

1. Feature hoặc behavior thứ nhất.
2. Feature hoặc behavior thứ hai.
3. Failure/recovery behavior.

## 7. Function và component

```java
Result execute(Command command);
void compensate(String operationId);
```

Với mỗi function quan trọng, ghi input, output, side effect, transaction boundary và
loại lỗi có thể trả về.

## 8. API và event contract

Ghi endpoint, method, request/response, error code và authentication. Với event, ghi
event name, version, partition key, schema và compatibility rule.

## 9. Data model và migration

Ghi table/entity, constraint, unique key, index, retention và migration/backfill cho dữ
liệu hiện có.

## 10. Configuration

Liệt kê timeout, TTL, batch size, retry limit hoặc feature flag. Ghi default, unit và
phạm vi hợp lệ; không hard-code secret.

## 11. Failure handling và recovery

| Failure | Detection | Immediate action | Recovery |
| --- | --- | --- | --- |
| Ví dụ | Exception/metric | Persist state | Retry/runbook |

Phân biệt business failure, transient failure, permanent failure và unknown outcome.

## 12. Non-Functional Requirements

- **Correctness:** Invariant phải luôn đúng.
- **Consistency:** Strong/eventual consistency ở boundary nào.
- **Concurrency:** Locking, version hoặc deduplication.
- **Performance:** Latency/throughput target và batch limit.
- **Availability:** Behavior khi dependency down.
- **Durability:** Dữ liệu nào không được mất.
- **Security:** Caller, authorization, secret và sensitive data.
- **Observability:** Trace, log, metric và alert.
- **Operability:** Recovery, replay, cleanup và runbook.
- **Maintainability:** Ownership, compatibility và cách mở rộng.

Chỉ giữ NFR có ý nghĩa và viết tiêu chí đo được khi đã có baseline.

## 13. Test strategy

- Unit test cho business rule.
- Integration test cho database/broker behavior.
- Contract test cho service hoặc event boundary.
- Concurrency/failure-injection test nếu phù hợp.
- Load test nếu topic có performance target.

## 14. Rollout và rollback

Mô tả thứ tự deploy, backward compatibility, feature flag, database migration và cách
quay lại version cũ mà không làm mất dữ liệu.

## 15. Acceptance criteria

- [ ] Happy path đạt yêu cầu.
- [ ] Failure path quan trọng được test.
- [ ] NFR quan trọng có bằng chứng.
- [ ] CI, dashboard và runbook đã cập nhật.
- [ ] Documentation khớp implementation.

## 16. Trade-off và lựa chọn khác

Ghi giải pháp được chọn, lựa chọn bị loại và điều kiện khiến quyết định cần xem xét lại.

## 17. Open questions

- Câu hỏi cần trả lời trước hoặc trong implementation.

## 18. References

- Link ADR, official documentation, API specification hoặc runbook liên quan.

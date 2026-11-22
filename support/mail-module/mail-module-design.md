# SMTP mail module and Notification ownership

Status: Accepted. Date: 2026-11-16. Decision owner: project owner.

## Context and decision

The owner requested a reusable mail module so Notification can focus on its
notification workflows. Extract the existing SMTP transport into `support/mail-module`;
retain business template selection and rendering inside Notification. This reuses
the service's existing SMTP deployment rather than introducing the MB Bank API.

```text
Order / Payment events → NotificationConsumer → MongoDB notification record
                                            → EmailService (Notification)
                                                → select and render Thymeleaf template
                                                → SmtpMailService (mail-module)
                                                → configured SMTP server
```

## Options and trade-offs

- Keep everything in Notification: simplest for one consumer, but does not provide
  the independent transport capability requested by the owner.
- Extract SMTP transport (selected): introduces one library dependency while
  keeping ownership visible. A concrete service and an immutable message are
  sufficient; no transport interface, resolver or factory is needed today.
- Copy the MB Bank module: would bring business templates, bank HTTP proxies,
  cross-support dependencies and legacy Spring/Java Mail APIs. These do not match
  the selected scope or the repository's independence rules.

## Ownership and configuration

`mail-module` owns MIME construction, UTF-8, sender/recipient headers and SMTP
submission through Spring's `JavaMailSender`. `MailMessage` contains prepared
subject/content and an HTML flag. Its recipient list is copied on construction.
The transport validates addresses and propagates failures. It neither logs message
content nor fetches tokens, renders templates, retries, schedules or persists work.
It has no dependency on common/web/proxy/caching/kafka/logging modules.

Notification owns event interpretation, recipients, subjects, Thymeleaf templates,
data variables, persistence and failure handling. It enables the transport explicitly
with `@EnableMailModule`; that annotation imports only the mail bean configuration.
Spring Boot configures the underlying sender with the existing `spring.mail.*`
settings. There is no network connection or email send during module registration.

Notification's `notification.email.sender-address` is configurable via
`MAIL_SENDER_ADDRESS`. Its local default preserves the sender previously embedded
in `EmailService`; override it with an address authorized by the SMTP provider.
Credentials remain runtime `spring.mail.*` configuration, outside source code.

## Compatibility and limits

Notification now uses durable MongoDB email jobs and a worker as described in
[Notification delivery](../../services/notification/notification-delivery.md), accepted by the owner after the
initial transport extraction. `SENT` records SMTP acceptance only. Retry, leases,
event identity and delivery state belong to Notification; the mail library still
propagates failures without retrying. The worker calls SMTP synchronously and the
consumer no longer launches detached async sends. Subjects/templates remain in
Notification. The events now include an additive eventId for deduplication.

Attachments and HTTP mail providers are not implemented in this initial scope.
Add them only for a demonstrated consumer. If an HTTP provider is later selected,
keep outbound authentication policy in the consuming service and do not introduce
a mail-to-proxy dependency silently. MB Bank reference files remain untouched.

## Verification

Run `./services/product/mvnw -f pom.xml -pl services/notification -am --batch-mode clean verify`.
Module tests construct real MIME messages with a mocked sender, verify HTML/plain
text, UTF-8, all recipients, sender headers, input checks and failure propagation.
Configuration tests verify explicit enablement without sending mail. Notification
tests render the actual business templates and capture the prepared transport
message, and the Notification delivery tests cover the selected retry/failure policy. No real email is sent by tests.

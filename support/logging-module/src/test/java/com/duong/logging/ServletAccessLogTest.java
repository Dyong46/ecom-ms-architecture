package com.duong.logging;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

@AutoConfigureObservability
@SpringBootTest(classes = AccessLogTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.web-application-type=servlet", "management.tracing.sampling.probability=1.0"})
class ServletAccessLogTest extends AccessLogHttpAssertions {
}

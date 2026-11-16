package com.duong.ecommerce.notification.delivery;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import java.time.Clock;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class DeliveryConfiguration {
    @Bean Clock deliveryClock() { return Clock.systemUTC(); }

    @Bean MongoClientSettingsBuilderCustomizer durableNotificationWrites() {
        return builder -> builder.writeConcern(WriteConcern.MAJORITY.withJournal(true))
                .readConcern(ReadConcern.MAJORITY);
    }
}

package com.duong.module.kafka.annotation;

import com.duong.module.kafka.config.KafkaModuleConfiguration;
import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(KafkaModuleConfiguration.class)
public @interface EnableKafkaModule {}

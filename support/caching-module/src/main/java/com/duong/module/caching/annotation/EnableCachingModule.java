package com.duong.module.caching.annotation;

import com.duong.module.caching.config.CachingModuleConfiguration;
import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CachingModuleConfiguration.class)
public @interface EnableCachingModule {}

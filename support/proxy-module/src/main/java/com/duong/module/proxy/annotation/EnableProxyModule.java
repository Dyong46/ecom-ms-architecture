package com.duong.module.proxy.annotation;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Register only the clients explicitly listed by the consuming application. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@EnableFeignClients
public @interface EnableProxyModule {
    @AliasFor(annotation = EnableFeignClients.class, attribute = "clients")
    Class<?>[] clients() default {};
}

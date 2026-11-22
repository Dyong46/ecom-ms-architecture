package com.duong.module.proxy.annotation;

import com.duong.module.proxy.ecom.config.EcomAuthConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Opt into shared Ecom OAuth only in applications that actually need tokens. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(EcomAuthConfiguration.class)
public @interface EnableEcomAuth {
}

package com.duong.module.web.config;

import com.duong.module.web.exception.advice.ExceptionControllerAdvice;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(ExceptionControllerAdvice.class)
public class WebModuleConfig {
}

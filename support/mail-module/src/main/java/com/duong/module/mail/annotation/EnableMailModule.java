package com.duong.module.mail.annotation;

import com.duong.module.mail.config.MailModuleConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MailModuleConfig.class)
public @interface EnableMailModule {
}

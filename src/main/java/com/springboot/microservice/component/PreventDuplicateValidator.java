package com.springboot.microservice.component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicateValidator {

    String[] includeFieldKeys() default {};

    String[] optionalValues() default {};

    long expireTime() default 10_000L;

}

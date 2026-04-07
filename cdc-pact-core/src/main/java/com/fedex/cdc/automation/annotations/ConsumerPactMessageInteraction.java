package com.fedex.cdc.automation.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConsumerPactMessageInteraction {
    String description();
    String providerState() default "";
    String destination();
    String[] metadata() default {"contentType=application/json"};
    Class<?> messageBody();
}




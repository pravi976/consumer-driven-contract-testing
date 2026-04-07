package com.fedex.cdc.automation.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConsumerPactInteraction {
    String description();
    String providerState() default "";
    String method();
    String path();
    int responseStatus() default 200;
    String[] query() default {};
    String[] requestHeaders() default {"Accept=application/json"};
    String[] responseHeaders() default {"Content-Type=application/json"};
    Class<?> requestBody() default Void.class;
    Class<?> responseBody();
}




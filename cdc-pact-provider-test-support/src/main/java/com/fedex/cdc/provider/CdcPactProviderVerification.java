package com.fedex.cdc.provider;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@ExtendWith(CdcPactProviderVerificationExtension.class)
public @interface CdcPactProviderVerification {
    String provider();

    String brokerUrlProperty() default "PACT_BROKER_BASE_URL";

    String brokerTokenProperty() default "PACT_BROKER_TOKEN";

    String providerVersionProperty() default "GITHUB_SHA";

    String providerBranchProperty() default "GITHUB_REF_NAME";

    String pactFolderProperty() default "PACT_FOLDER";

    boolean publishResults() default false;
}
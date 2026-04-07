package com.fedex.cdc.provider;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

public class CdcPactProviderVerificationExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        CdcPactProviderVerification verification = context.getRequiredTestClass()
                .getAnnotation(CdcPactProviderVerification.class);
        if (verification == null) {
            return;
        }

        setProperty("pact.provider.name", verification.provider());
        setFromEnvironment("pactbroker.url", verification.brokerUrlProperty());
        setFromEnvironment("pactbroker.auth.token", verification.brokerTokenProperty());
        setFromEnvironment("pact.provider.version", verification.providerVersionProperty());
        setFromEnvironment("pact.provider.branch", verification.providerBranchProperty());
        setFromEnvironment("pact.folder", verification.pactFolderProperty());
        setProperty("pact.verifier.publishResults", Boolean.toString(verification.publishResults()));
    }

    private void setFromEnvironment(String systemProperty, String environmentProperty) {
        Optional.ofNullable(System.getProperty(environmentProperty))
                .or(() -> Optional.ofNullable(System.getenv(environmentProperty)))
                .ifPresent(value -> setProperty(systemProperty, value));
    }

    private void setProperty(String key, String value) {
        if (value != null && !value.isBlank()) {
            System.setProperty(key, value);
        }
    }
}
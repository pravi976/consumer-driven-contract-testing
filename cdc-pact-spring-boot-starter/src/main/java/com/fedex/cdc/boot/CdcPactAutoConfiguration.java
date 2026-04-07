package com.fedex.cdc.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = "com.fedex.cdc.automation")
public class CdcPactAutoConfiguration {
}

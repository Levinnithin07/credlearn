package com.credlearn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** A private deployment setting, never a value supplied by registration or the browser. */
@ConfigurationProperties(prefix = "credlearn.admin")
public record AdminOwnerProperties(Long ownerUserId) {}

package com.rampay.paymentservice.config;

/**
 * OpenTelemetry tracing is configured entirely through Spring Boot's auto-configuration.
 * See management.tracing and management.otlp.tracing properties in application.yaml.
 *
 * Dependencies providing auto-config:
 *   - micrometer-tracing-bridge-otel
 *   - opentelemetry-exporter-otlp
 */
public class OpenTelemetryConfig {
    // intentionally empty — no manual beans needed
}

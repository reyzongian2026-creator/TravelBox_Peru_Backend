package com.tuempresa.storage.shared.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter paymentSuccessCounter(MeterRegistry registry) {
        return Counter.builder("travelbox.payments.success")
                .description("Total number of successful payments")
                .register(registry);
    }

    @Bean
    public Counter paymentFailureCounter(MeterRegistry registry) {
        return Counter.builder("travelbox.payments.failure")
                .description("Total number of failed payments")
                .register(registry);
    }

    @Bean
    public Timer paymentProcessingDuration(MeterRegistry registry) {
        return Timer.builder("travelbox.payments.processing.duration")
                .description("Time taken to process a payment")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Counter reservationsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("travelbox.reservations.created")
                .description("Total number of reservations created")
                .register(registry);
    }
}

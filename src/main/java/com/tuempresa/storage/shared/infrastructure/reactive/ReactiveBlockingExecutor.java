package com.tuempresa.storage.shared.infrastructure.reactive;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

@Component
public class ReactiveBlockingExecutor {

    private final Scheduler scheduler;
    private final Duration operationTimeout;

    public ReactiveBlockingExecutor(
            @Value("${app.reactive.blocking.max-threads:96}") int maxThreads,
            @Value("${app.reactive.blocking.max-queue:20000}") int maxQueue,
            @Value("${app.reactive.blocking.thread-ttl-seconds:60}") int threadTtlSeconds,
            @Value("${app.reactive.blocking.timeout-ms:30000}") long timeoutMs
    ) {
        int safeThreads = Math.max(8, maxThreads);
        int safeQueue = Math.max(1000, maxQueue);
        int safeTtl = Math.max(15, threadTtlSeconds);
        this.scheduler = Schedulers.newBoundedElastic(
                safeThreads,
                safeQueue,
                "tbx-blocking",
                safeTtl
        );
        this.operationTimeout = Duration.ofMillis(Math.max(1000L, timeoutMs));
    }

    public <T> Mono<T> call(Callable<T> callable) {
        return Mono.fromCallable(callable)
                .subscribeOn(scheduler)
                .timeout(operationTimeout)
                .onErrorMap(
                        TimeoutException.class,
                        ex -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "BLOCKING_OPERATION_TIMEOUT",
                                "La operacion tardo demasiado en completarse."
                        )
                )
                .onErrorMap(
                        RejectedExecutionException.class,
                        ex -> new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "SERVER_BUSY",
                                "El servidor esta atendiendo demasiadas solicitudes. Intenta nuevamente."
                        )
                );
    }

    @PreDestroy
    public void shutdown() {
        scheduler.dispose();
    }
}

package com.tuempresa.storage.notifications.application.email;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Component
@Lazy(false)
public class EmailOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxScheduler.class);
    private static final AtomicInteger WORKER_THREAD_INDEX = new AtomicInteger(0);

    private final EmailOutboxService emailOutboxService;
    private final EmailOutboxWorker emailOutboxWorker;
    private final boolean queueEnabled;
    private final int batchSize;
    private final ExecutorService workerExecutor;
    private final Set<Long> inFlightIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmailOutboxScheduler(
            EmailOutboxService emailOutboxService,
            EmailOutboxWorker emailOutboxWorker,
            @Value("${app.email.queue.enabled:true}") boolean queueEnabled,
            @Value("${app.email.queue.batch-size:20}") int batchSize,
            @Value("${app.email.queue.worker-threads:4}") int workerThreads
    ) {
        this.emailOutboxService = emailOutboxService;
        this.emailOutboxWorker = emailOutboxWorker;
        this.queueEnabled = queueEnabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        int safeWorkerThreads = Math.max(1, Math.min(workerThreads, 32));
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("tbx-email-outbox-" + WORKER_THREAD_INDEX.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.workerExecutor = Executors.newFixedThreadPool(safeWorkerThreads, threadFactory);
    }

    @Scheduled(fixedDelayString = "${app.email.queue.poll-ms:3000}")
    public void processOutbox() {
        if (!queueEnabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<Long> readyIds = emailOutboxService.findReadyIds(batchSize);
            for (Long readyId : readyIds) {
                if (readyId == null || !inFlightIds.add(readyId)) {
                    continue;
                }
                try {
                    workerExecutor.execute(() -> processSingle(readyId));
                } catch (RejectedExecutionException ex) {
                    inFlightIds.remove(readyId);
                    log.warn("Pool de correo saturado; se reintentara email_outbox {}", readyId);
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void processSingle(Long readyId) {
        try {
            emailOutboxWorker.processById(readyId);
        } catch (Exception ex) {
            log.warn("Error procesando email_outbox {}: {}", readyId, ex.getMessage());
        } finally {
            inFlightIds.remove(readyId);
        }
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            workerExecutor.shutdownNow();
        }
    }
}

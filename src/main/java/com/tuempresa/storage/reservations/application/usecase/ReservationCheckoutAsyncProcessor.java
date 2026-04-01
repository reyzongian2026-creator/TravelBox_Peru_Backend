package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import com.tuempresa.storage.payments.application.usecase.PaymentService;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ReservationCheckoutAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReservationCheckoutAsyncProcessor.class);

    private final PaymentService paymentService;
    private final ExecutorService workerExecutor;

    public ReservationCheckoutAsyncProcessor(
            PaymentService paymentService,
            @Value("${app.reservations.checkout-confirm-workers:2}") int workerThreads
    ) {
        this.paymentService = paymentService;
        int safeWorkers = Math.max(1, workerThreads);
        this.workerExecutor = Executors.newFixedThreadPool(
                safeWorkers,
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("reservation-checkout-async-" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    public void confirmAsync(ConfirmPaymentRequest request, AuthUserPrincipal principal) {
        if (request == null || principal == null) {
            return;
        }
        workerExecutor.execute(() -> {
            try {
                paymentService.confirm(request, principal);
            } catch (Exception ex) {
                log.warn(
                        "Fallo confirmacion asincrona de pago checkout. reservationId={}, paymentIntentId={}",
                        request.reservationId(),
                        request.paymentIntentId(),
                        ex
                );
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

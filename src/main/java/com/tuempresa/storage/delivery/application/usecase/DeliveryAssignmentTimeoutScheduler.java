package com.tuempresa.storage.delivery.application.usecase;

import com.tuempresa.storage.delivery.domain.DeliveryOrder;
import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Resets ASSIGNED delivery orders that have not progressed
 * (no IN_TRANSIT update) within the configured timeout,
 * returning them to REQUESTED so another courier can claim them.
 */
@Component
@Lazy(false)
public class DeliveryAssignmentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAssignmentTimeoutScheduler.class);
    private static final int TIMEOUT_MINUTES = 30;

    private final DeliveryOrderRepository deliveryOrderRepository;

    public DeliveryAssignmentTimeoutScheduler(DeliveryOrderRepository deliveryOrderRepository) {
        this.deliveryOrderRepository = deliveryOrderRepository;
    }

    @Scheduled(fixedDelayString = "${app.delivery.assignment-timeout-check-ms:300000}")
    @Transactional
    public void resetStaleAssignments() {
        try {
            Instant cutoff = Instant.now().minus(TIMEOUT_MINUTES, ChronoUnit.MINUTES);
            List<DeliveryOrder> stale = deliveryOrderRepository
                    .findByStatusAndUpdatedAtBefore(DeliveryStatus.ASSIGNED, cutoff);

            for (DeliveryOrder order : stale) {
                order.resetAssignment();
                deliveryOrderRepository.save(order);
            }

            if (!stale.isEmpty()) {
                log.info("Reset {} stale ASSIGNED delivery orders back to REQUESTED.", stale.size());
            }
        } catch (Exception ex) {
            log.error("Delivery assignment timeout scheduler failed; keeping service alive.", ex);
        }
    }
}

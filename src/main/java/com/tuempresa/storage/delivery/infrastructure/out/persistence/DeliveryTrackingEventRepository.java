package com.tuempresa.storage.delivery.infrastructure.out.persistence;

import com.tuempresa.storage.delivery.domain.DeliveryTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryTrackingEventRepository extends JpaRepository<DeliveryTrackingEvent, Long> {

    List<DeliveryTrackingEvent> findByDeliveryOrderIdOrderBySequenceNumberAsc(Long deliveryOrderId);
}

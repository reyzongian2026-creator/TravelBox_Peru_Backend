package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import com.tuempresa.storage.payments.application.dto.CreatePaymentIntentRequest;
import com.tuempresa.storage.payments.application.usecase.PaymentService;
import com.tuempresa.storage.reservations.application.dto.CreateReservationCheckoutRequest;
import com.tuempresa.storage.reservations.application.dto.ReservationResponse;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReservationCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(ReservationCheckoutService.class);

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final ReservationCheckoutAsyncProcessor reservationCheckoutAsyncProcessor;

    public ReservationCheckoutService(
            ReservationService reservationService,
            PaymentService paymentService,
            ReservationCheckoutAsyncProcessor reservationCheckoutAsyncProcessor) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.reservationCheckoutAsyncProcessor = reservationCheckoutAsyncProcessor;
    }

    public ReservationResponse checkout(CreateReservationCheckoutRequest request, AuthUserPrincipal principal) {
        ReservationResponse created = reservationService.create(request.toReservationRequest(), principal);
        Long reservationId = created.id();
        try {
            Long paymentIntentId = paymentService
                    .createIntent(new CreatePaymentIntentRequest(reservationId, null, null), principal)
                    .id();
            ConfirmPaymentRequest basePaymentRequest = request.toPaymentRequest(reservationId);
            ConfirmPaymentRequest asyncPaymentRequest = new ConfirmPaymentRequest(
                    paymentIntentId,
                    reservationId,
                    basePaymentRequest.approved(),
                    basePaymentRequest.providerReference(),
                    basePaymentRequest.paymentMethod(),
                    basePaymentRequest.sourceTokenId(),
                    basePaymentRequest.customerEmail(),
                    basePaymentRequest.customerFirstName(),
                    basePaymentRequest.customerLastName(),
                    basePaymentRequest.customerPhone(),
                    basePaymentRequest.customerDocument());
            reservationCheckoutAsyncProcessor.confirmAsync(asyncPaymentRequest, principal);
        } catch (Exception ex) {
            log.warn(
                    "No se pudo iniciar flujo asincrono de pago checkout para reserva {}. La reserva queda pendiente de pago.",
                    reservationId,
                    ex);
        }
        return reservationService.getById(reservationId, principal);
    }
}

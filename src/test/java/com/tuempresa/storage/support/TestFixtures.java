package com.tuempresa.storage.support;

import com.tuempresa.storage.payments.domain.BookingType;
import com.tuempresa.storage.payments.domain.CancellationPolicyType;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationBagSize;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.warehouses.domain.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralised factory methods for domain objects used across test suites.
 *
 * <p>Every factory produces a valid, minimal entity instance that can be
 * further customised via its public mutators or via the provided builder
 * helpers. Objects returned here are <b>not persisted</b> -- tests that
 * need database rows should save them through the relevant repository.</p>
 */
public final class TestFixtures {

    private TestFixtures() {
        // utility class
    }

    // =========================================================================
    // Sequences -- deterministic ids for tests that need uniqueness
    // =========================================================================

    private static final AtomicLong USER_SEQ = new AtomicLong(1);
    private static final AtomicLong WAREHOUSE_SEQ = new AtomicLong(1);

    /**
     * Resets all internal sequences. Call from {@code @BeforeEach} when tests
     * rely on deterministic generated values.
     */
    public static void resetSequences() {
        USER_SEQ.set(1);
        WAREHOUSE_SEQ.set(1);
    }

    // =========================================================================
    // User factories
    // =========================================================================

    /**
     * Returns a minimal active client {@link User} with a unique email.
     */
    public static User aClientUser() {
        long seq = USER_SEQ.getAndIncrement();
        return User.of(
                "Test Client " + seq,
                "client" + seq + "@test.travelbox.pe",
                "$2a$10$dummyHashForTestPurposesOnly000000000000000000000",
                "+5199900" + String.format("%04d", seq),
                Set.of(Role.CLIENT)
        );
    }

    /**
     * Returns a minimal active admin {@link User}.
     */
    public static User anAdminUser() {
        long seq = USER_SEQ.getAndIncrement();
        return User.of(
                "Test Admin " + seq,
                "admin" + seq + "@test.travelbox.pe",
                "$2a$10$dummyHashForTestPurposesOnly000000000000000000000",
                "+5199800" + String.format("%04d", seq),
                Set.of(Role.ADMIN)
        );
    }

    /**
     * Returns a minimal active operator {@link User}.
     */
    public static User anOperatorUser() {
        long seq = USER_SEQ.getAndIncrement();
        return User.of(
                "Test Operator " + seq,
                "operator" + seq + "@test.travelbox.pe",
                "$2a$10$dummyHashForTestPurposesOnly000000000000000000000",
                "+5199700" + String.format("%04d", seq),
                Set.of(Role.OPERATOR)
        );
    }

    /**
     * Returns a minimal active courier {@link User}.
     */
    public static User aCourierUser() {
        long seq = USER_SEQ.getAndIncrement();
        return User.of(
                "Test Courier " + seq,
                "courier" + seq + "@test.travelbox.pe",
                "$2a$10$dummyHashForTestPurposesOnly000000000000000000000",
                "+5199600" + String.format("%04d", seq),
                Set.of(Role.COURIER)
        );
    }

    /**
     * Returns a {@link User} with the given roles.
     */
    public static User aUserWithRoles(Set<Role> roles) {
        long seq = USER_SEQ.getAndIncrement();
        return User.of(
                "Test User " + seq,
                "user" + seq + "@test.travelbox.pe",
                "$2a$10$dummyHashForTestPurposesOnly000000000000000000000",
                "+5199500" + String.format("%04d", seq),
                roles
        );
    }

    /**
     * Returns a client user whose profile fields are fully populated.
     */
    public static User aClientUserWithCompletedProfile() {
        User user = aClientUser();
        user.applyRegistrationDetails(
                user.getFirstName() != null ? user.getFirstName() : "Maria",
                user.getLastName() != null ? user.getLastName() : "Garcia",
                "PE",
                "es",
                user.getPhone(),
                true,
                null
        );
        user.markEmailVerified();
        user.markOnboardingCompleted();
        return user;
    }

    // =========================================================================
    // Warehouse factory
    // =========================================================================

    /**
     * Returns a minimal active {@link Warehouse} with default pricing.
     * The city/zone dependencies are set to {@code null}; tests that need
     * them persisted should create {@code City} / {@code TouristZone}
     * instances separately.
     */
    public static Warehouse aWarehouse() {
        long seq = WAREHOUSE_SEQ.getAndIncrement();
        return Warehouse.of(
                null,   // city  -- supply separately when persistence is needed
                null,   // zone
                "Test Warehouse " + seq,
                "Av. Test " + seq + ", Cusco",
                -13.52 + (seq * 0.001),
                -71.97 + (seq * 0.001),
                20,
                "08:00",
                "20:00",
                "No items prohibited",
                Warehouse.DEFAULT_PRICE_SMALL_PER_HOUR,
                Warehouse.DEFAULT_PRICE_MEDIUM_PER_HOUR,
                Warehouse.DEFAULT_PRICE_LARGE_PER_HOUR,
                Warehouse.DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR,
                Warehouse.DEFAULT_PICKUP_FEE,
                Warehouse.DEFAULT_DROPOFF_FEE,
                Warehouse.DEFAULT_INSURANCE_FEE
        );
    }

    /**
     * Returns a warehouse with custom capacity.
     */
    public static Warehouse aWarehouseWithCapacity(int capacity) {
        long seq = WAREHOUSE_SEQ.getAndIncrement();
        return Warehouse.of(
                null,
                null,
                "Test Warehouse " + seq,
                "Av. Test " + seq + ", Cusco",
                -13.52 + (seq * 0.001),
                -71.97 + (seq * 0.001),
                capacity,
                "08:00",
                "20:00",
                null,
                null, null, null, null,
                null, null, null
        );
    }

    // =========================================================================
    // Reservation factory
    // =========================================================================

    /**
     * Returns a {@link Reservation} in {@code PENDING_PAYMENT} status with
     * sensible defaults. Both the {@code user} and {@code warehouse} must be
     * supplied (they are required FK references).
     */
    public static Reservation aPendingReservation(User user, Warehouse warehouse) {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        BigDecimal storageAmount = new BigDecimal("18.00");
        return Reservation.createPendingPayment(
                user,
                warehouse,
                start,
                end,
                storageAmount,
                1,
                ReservationBagSize.MEDIUM,
                false,
                false,
                false,
                storageAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                start.plus(30, ChronoUnit.MINUTES)
        );
    }

    /**
     * Returns a reservation with pickup + dropoff + insurance services enabled.
     */
    public static Reservation aFullServiceReservation(User user, Warehouse warehouse) {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(6, ChronoUnit.HOURS);
        BigDecimal storageAmount = new BigDecimal("27.00");
        BigDecimal pickupFee = Warehouse.DEFAULT_PICKUP_FEE;
        BigDecimal dropoffFee = Warehouse.DEFAULT_DROPOFF_FEE;
        BigDecimal insuranceFee = Warehouse.DEFAULT_INSURANCE_FEE;
        BigDecimal total = storageAmount.add(pickupFee).add(dropoffFee).add(insuranceFee);
        return Reservation.createPendingPayment(
                user,
                warehouse,
                start,
                end,
                total,
                2,
                ReservationBagSize.LARGE,
                true,
                true,
                true,
                storageAmount,
                pickupFee,
                dropoffFee,
                insuranceFee,
                start.plus(30, ChronoUnit.MINUTES)
        );
    }

    // =========================================================================
    // PaymentAttempt factory
    // =========================================================================

    /**
     * Returns a {@link PaymentAttempt} in {@code PENDING} status linked to
     * the given reservation with its total price as the amount.
     */
    public static PaymentAttempt aPendingPayment(Reservation reservation) {
        return PaymentAttempt.pending(reservation, reservation.getTotalPrice());
    }

    /**
     * Returns a {@link PaymentAttempt} in {@code PENDING} status with an
     * explicit amount.
     */
    public static PaymentAttempt aPendingPayment(Reservation reservation, BigDecimal amount) {
        return PaymentAttempt.pending(reservation, amount);
    }

    /**
     * Returns a confirmed {@link PaymentAttempt} linked to the given
     * reservation.
     */
    public static PaymentAttempt aConfirmedPayment(Reservation reservation) {
        PaymentAttempt payment = PaymentAttempt.pending(reservation, reservation.getTotalPrice());
        payment.confirm("PROVIDER-CONFIRMED-TEST");
        return payment;
    }

    /**
     * Returns a failed {@link PaymentAttempt}.
     */
    public static PaymentAttempt aFailedPayment(Reservation reservation) {
        PaymentAttempt payment = PaymentAttempt.pending(reservation, reservation.getTotalPrice());
        payment.fail("PROVIDER-FAILED-TEST");
        return payment;
    }

    /**
     * Returns a fully-refunded {@link PaymentAttempt}.
     */
    public static PaymentAttempt aRefundedPayment(Reservation reservation) {
        PaymentAttempt payment = aConfirmedPayment(reservation);
        payment.refund(
                "PROVIDER-REFUND-TEST",
                reservation.getTotalPrice(),
                BigDecimal.ZERO,
                "Test refund"
        );
        return payment;
    }

    /**
     * Returns a confirmed payment with promo code discount applied.
     */
    public static PaymentAttempt aPaymentWithDiscount(Reservation reservation, BigDecimal discountAmount) {
        PaymentAttempt payment = PaymentAttempt.pending(
                reservation,
                reservation.getTotalPrice().subtract(discountAmount)
        );
        payment.setDiscountAmount(discountAmount);
        payment.confirm("PROVIDER-DISCOUNT-TEST");
        return payment;
    }

    // =========================================================================
    // Common BigDecimal helpers
    // =========================================================================

    /**
     * Shorthand for {@code new BigDecimal(value)} -- reduces boilerplate in
     * assertions.
     */
    public static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /**
     * Returns an {@link Instant} that is {@code hoursFromNow} hours in the
     * future. Negative values produce past instants.
     */
    public static Instant hoursFromNow(int hoursFromNow) {
        return Instant.now().plus(hoursFromNow, ChronoUnit.HOURS);
    }

    /**
     * Returns an {@link Instant} that is {@code daysFromNow} days in the
     * future. Negative values produce past instants.
     */
    public static Instant daysFromNow(int daysFromNow) {
        return Instant.now().plus(daysFromNow, ChronoUnit.DAYS);
    }
}

package com.tuempresa.storage.reports.application.usecase;

import com.tuempresa.storage.delivery.domain.DeliveryOrder;
import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import com.tuempresa.storage.incidents.domain.Incident;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.incidents.infrastructure.out.persistence.IncidentRepository;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reports.application.dto.AdminDashboardResponse;
import com.tuempresa.storage.reports.application.dto.AdminDashboardSummaryResponse;
import com.tuempresa.storage.reports.application.dto.AdminRankingsResponse;
import com.tuempresa.storage.reports.application.dto.AdminTrendsResponse;
import com.tuempresa.storage.reports.application.dto.DashboardPeriod;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Lima");
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReservationRepository reservationRepository;
    private final IncidentRepository incidentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public AdminDashboardService(
            UserRepository userRepository,
            WarehouseRepository warehouseRepository,
            ReservationRepository reservationRepository,
            IncidentRepository incidentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            DeliveryOrderRepository deliveryOrderRepository
    ) {
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.reservationRepository = reservationRepository;
        this.incidentRepository = incidentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.deliveryOrderRepository = deliveryOrderRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(String rawPeriod) {
        DashboardPeriod period = DashboardPeriod.from(rawPeriod);
        String cacheKey = "dashboard_" + period.code();
        
        CacheEntry<AdminDashboardResponse> cached = (CacheEntry<AdminDashboardResponse>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Dashboard cache hit for period: {}", period.code());
            return cached.response;
        }

        log.debug("Building dashboard for period: {}", period.code());
        AdminDashboardResponse response = buildDashboard(period);
        cache.put(cacheKey, new CacheEntry<>(response));
        
        return response;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse buildSummary(String rawPeriod) {
        DashboardPeriod period = DashboardPeriod.from(rawPeriod);
        String cacheKey = "dashboard_summary_" + period.code();
        
        CacheEntry<AdminDashboardSummaryResponse> cached = (CacheEntry<AdminDashboardSummaryResponse>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Summary cache hit for period: {}", period.code());
            return cached.response;
        }

        log.debug("Building summary for period: {}", period.code());
        Instant now = Instant.now();
        Instant periodStartAt = period.startAt(now, REPORT_ZONE);

        // Use optimized queries without loading full reservations
        List<Reservation> periodReservations = reservationRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(periodStartAt, now);
        Set<Long> periodReservationIds = periodReservations.stream()
                .map(Reservation::getId)
                .collect(Collectors.toSet());

        List<PaymentAttempt> periodConfirmedPayments = periodReservationIds.isEmpty()
                ? List.of()
                : paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(
                        periodReservationIds,
                        PaymentStatus.CONFIRMED
                );
        List<Incident> periodIncidents = periodReservationIds.isEmpty()
                ? List.of()
                : incidentRepository.findByReservationIdIn(periodReservationIds);
        List<Incident> periodOpenIncidents = periodReservationIds.isEmpty()
                ? List.of()
                : incidentRepository.findByReservationIdInAndStatus(periodReservationIds, IncidentStatus.OPEN);

        long totalUsers = userRepository.count();
        long totalWarehouses = warehouseRepository.count();
        long activeReservations = reservationRepository.countByStatusNotIn(INACTIVE_RESERVATION_STATUSES);
        long openIncidents = incidentRepository.countByStatus(IncidentStatus.OPEN);
        BigDecimal confirmedPayments = paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED);
        if (confirmedPayments == null) {
            confirmedPayments = BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> confirmedRevenueByReservation = resolveConfirmedRevenueByReservation(periodConfirmedPayments);
        Map<Long, Long> incidentCountByReservation = periodIncidents.stream()
                .filter(incident -> incident.getReservation() != null)
                .collect(Collectors.groupingBy(incident -> incident.getReservation().getId(), Collectors.counting()));
        Map<Long, Long> openIncidentCountByReservation = periodOpenIncidents.stream()
                .filter(incident -> incident.getReservation() != null)
                .collect(Collectors.groupingBy(incident -> incident.getReservation().getId(), Collectors.counting()));

        long completedReservations = periodReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.COMPLETED)
                .count();
        long cancelledReservations = periodReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CANCELLED)
                .count();
        long incidentReservations = periodReservations.stream()
                .filter(reservation -> incidentCountByReservation.getOrDefault(reservation.getId(), 0L) > 0
                        || reservation.getStatus() == ReservationStatus.INCIDENT)
                .count();
        long pendingPaymentReservations = periodReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.PENDING_PAYMENT)
                .count();
        long activeReservationsInPeriod = periodReservations.stream()
                .filter(this::isActiveReservation)
                .count();
        long uniqueClients = periodReservations.stream()
                .map(reservation -> reservation.getUser().getId())
                .distinct()
                .count();
        long openIncidentsInPeriod = periodReservations.stream()
                .mapToLong(reservation -> openIncidentCountByReservation.getOrDefault(reservation.getId(), 0L))
                .sum();

        BigDecimal confirmedRevenueInPeriod = periodReservations.stream()
                .map(reservation -> confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long paidReservations = periodReservations.stream()
                .filter(reservation -> confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
                        .compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal averageTicket = paidReservations == 0
                ? BigDecimal.ZERO
                : confirmedRevenueInPeriod.divide(BigDecimal.valueOf(paidReservations), 2, RoundingMode.HALF_UP);

        AdminDashboardSummaryResponse response = new AdminDashboardSummaryResponse(
                period.code(),
                period.label(),
                now,
                new AdminDashboardSummaryResponse.Summary(
                        periodReservations.size(),
                        activeReservationsInPeriod,
                        completedReservations,
                        cancelledReservations,
                        incidentReservations,
                        pendingPaymentReservations,
                        uniqueClients,
                        openIncidentsInPeriod,
                        confirmedRevenueInPeriod,
                        averageTicket,
                        ratio(completedReservations, periodReservations.size()),
                        ratio(cancelledReservations, periodReservations.size())
                ),
                totalUsers,
                totalWarehouses,
                activeReservations,
                openIncidents,
                confirmedPayments
        );

        cache.put(cacheKey, new CacheEntry<>(response));
        return response;
    }

    @Transactional(readOnly = true)
    public AdminRankingsResponse buildRankings(String rawPeriod, int limit) {
        DashboardPeriod period = DashboardPeriod.from(rawPeriod);
        String cacheKey = "dashboard_rankings_" + period.code() + "_" + limit;
        
        CacheEntry<AdminRankingsResponse> cached = (CacheEntry<AdminRankingsResponse>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Rankings cache hit for period: {}", period.code());
            return cached.response;
        }

        log.debug("Building rankings for period: {} with limit: {}", period.code(), limit);
        Instant now = Instant.now();
        Instant periodStartAt = period.startAt(now, REPORT_ZONE);

        // Get rankings with database optimization
        List<Reservation> periodReservations = reservationRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(periodStartAt, now);
        Set<Long> periodReservationIds = periodReservations.stream()
                .map(Reservation::getId)
                .collect(Collectors.toSet());

        List<DeliveryOrder> periodDeliveries = deliveryOrderRepository
                .findByUpdatedAtBetweenOrderByUpdatedAtDesc(periodStartAt, now);

        List<PaymentAttempt> periodConfirmedPayments = periodReservationIds.isEmpty()
                ? List.of()
                : paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(
                        periodReservationIds,
                        PaymentStatus.CONFIRMED
                );
        List<Incident> periodIncidents = periodReservationIds.isEmpty()
                ? List.of()
                : incidentRepository.findByReservationIdIn(periodReservationIds);

        Map<Long, BigDecimal> confirmedRevenueByReservation = resolveConfirmedRevenueByReservation(periodConfirmedPayments);
        Map<Long, Long> incidentCountByReservation = periodIncidents.stream()
                .filter(incident -> incident.getReservation() != null)
                .collect(Collectors.groupingBy(incident -> incident.getReservation().getId(), Collectors.counting()));

        // Build warehouse ranking
        List<AdminRankingsResponse.RankingItem> warehouseRanking = buildWarehouseRankingItems(
                periodReservations,
                confirmedRevenueByReservation,
                incidentCountByReservation,
                limit
        );

        // Build city ranking
        List<AdminRankingsResponse.RankingItem> cityRanking = buildCityRankingItems(
                periodReservations,
                confirmedRevenueByReservation,
                incidentCountByReservation,
                limit
        );

        // Build courier ranking
        List<AdminRankingsResponse.RankingItem> courierRanking = buildCourierRankingItems(
                periodDeliveries,
                limit
        );

        // Build operator ranking
        List<AdminRankingsResponse.RankingItem> operatorRanking = buildOperatorRankingItems(
                periodDeliveries,
                limit
        );

        AdminRankingsResponse response = new AdminRankingsResponse(
                period.code(),
                period.label(),
                now,
                warehouseRanking,
                cityRanking,
                courierRanking,
                operatorRanking
        );

        cache.put(cacheKey, new CacheEntry<>(response));
        return response;
    }

    @Transactional(readOnly = true)
    public AdminTrendsResponse buildTrends(String rawPeriod) {
        DashboardPeriod period = DashboardPeriod.from(rawPeriod);
        String cacheKey = "dashboard_trends_" + period.code();
        
        CacheEntry<AdminTrendsResponse> cached = (CacheEntry<AdminTrendsResponse>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Trends cache hit for period: {}", period.code());
            return cached.response;
        }

        log.debug("Building trends for period: {}", period.code());
        Instant now = Instant.now();
        Instant periodStartAt = period.startAt(now, REPORT_ZONE);

        List<Reservation> periodReservations = reservationRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(periodStartAt, now);
        Set<Long> periodReservationIds = periodReservations.stream()
                .map(Reservation::getId)
                .collect(Collectors.toSet());

        List<PaymentAttempt> periodConfirmedPayments = periodReservationIds.isEmpty()
                ? List.of()
                : paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(
                        periodReservationIds,
                        PaymentStatus.CONFIRMED
                );
        List<Incident> periodIncidents = periodReservationIds.isEmpty()
                ? List.of()
                : incidentRepository.findByReservationIdIn(periodReservationIds);

        Map<Long, BigDecimal> confirmedRevenueByReservation = resolveConfirmedRevenueByReservation(periodConfirmedPayments);
        Map<Long, Long> incidentCountByReservation = periodIncidents.stream()
                .filter(incident -> incident.getReservation() != null)
                .collect(Collectors.groupingBy(incident -> incident.getReservation().getId(), Collectors.counting()));

        List<AdminTrendsResponse.TrendPoint> trends = buildTrendItems(period, periodReservations, confirmedRevenueByReservation, incidentCountByReservation);

        AdminTrendsResponse response = new AdminTrendsResponse(
                period.code(),
                period.label(),
                now,
                trends,
                List.of()
        );

        cache.put(cacheKey, new CacheEntry<>(response));
        return response;
    }

    private AdminDashboardResponse buildDashboard(DashboardPeriod period) {
        Instant now = Instant.now();
        Instant periodStartAt = period.startAt(now, REPORT_ZONE);

        List<Reservation> periodReservations = reservationRepository
                .findByStartAtBetweenOrderByStartAtAsc(periodStartAt, now);
        Set<Long> periodReservationIds = periodReservations.stream()
                .map(Reservation::getId)
                .collect(Collectors.toSet());

        List<Incident> periodIncidents = periodReservationIds.isEmpty()
                ? List.of()
                : incidentRepository.findByReservationIdIn(periodReservationIds);
        List<Incident> periodOpenIncidents = periodReservationIds.isEmpty()
                ? List.of()
                : incidentRepository.findByReservationIdInAndStatus(periodReservationIds, IncidentStatus.OPEN);
        List<PaymentAttempt> periodConfirmedPayments = periodReservationIds.isEmpty()
                ? List.of()
                : paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(
                        periodReservationIds,
                        PaymentStatus.CONFIRMED
                );
        List<DeliveryOrder> periodDeliveries = deliveryOrderRepository
                .findByUpdatedAtBetweenOrderByUpdatedAtDesc(periodStartAt, now);

        long totalUsers = userRepository.count();
        long totalWarehouses = warehouseRepository.count();
        long activeReservations = reservationRepository.countByStatusNotIn(INACTIVE_RESERVATION_STATUSES);
        long openIncidents = incidentRepository.countByStatus(IncidentStatus.OPEN);
        BigDecimal confirmedPayments = paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED);
        if (confirmedPayments == null) {
            confirmedPayments = BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> confirmedRevenueByReservation = resolveConfirmedRevenueByReservation(periodConfirmedPayments);
        Map<Long, Long> incidentCountByReservation = periodIncidents.stream()
                .collect(Collectors.groupingBy(incident -> incident.getReservation().getId(), Collectors.counting()));
        Map<Long, Long> openIncidentCountByReservation = periodOpenIncidents.stream()
                .collect(Collectors.groupingBy(incident -> incident.getReservation().getId(), Collectors.counting()));

        long completedReservations = periodReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.COMPLETED)
                .count();
        long cancelledReservations = periodReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CANCELLED)
                .count();
        long incidentReservations = periodReservations.stream()
                .filter(reservation -> incidentCountByReservation.getOrDefault(reservation.getId(), 0L) > 0
                        || reservation.getStatus() == ReservationStatus.INCIDENT)
                .count();
        long pendingPaymentReservations = periodReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.PENDING_PAYMENT)
                .count();
        long activeReservationsInPeriod = periodReservations.stream()
                .filter(this::isActiveReservation)
                .count();
        long uniqueClients = periodReservations.stream()
                .map(reservation -> reservation.getUser().getId())
                .distinct()
                .count();
        long openIncidentsInPeriod = periodReservations.stream()
                .mapToLong(reservation -> openIncidentCountByReservation.getOrDefault(reservation.getId(), 0L))
                .sum();

        BigDecimal confirmedRevenueInPeriod = periodReservations.stream()
                .map(reservation -> confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long paidReservations = periodReservations.stream()
                .filter(reservation -> confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
                        .compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal averageTicket = paidReservations == 0
                ? BigDecimal.ZERO
                : confirmedRevenueInPeriod.divide(BigDecimal.valueOf(paidReservations), 2, RoundingMode.HALF_UP);

        List<AdminDashboardResponse.WarehousePerformance> topWarehouses = buildWarehouseRanking(
                periodReservations,
                confirmedRevenueByReservation,
                incidentCountByReservation
        );

        return new AdminDashboardResponse(
                period.code(),
                period.label(),
                now,
                new AdminDashboardResponse.Summary(
                        periodReservations.size(),
                        activeReservationsInPeriod,
                        completedReservations,
                        cancelledReservations,
                        incidentReservations,
                        pendingPaymentReservations,
                        uniqueClients,
                        openIncidentsInPeriod,
                        confirmedRevenueInPeriod,
                        averageTicket,
                        ratio(completedReservations, periodReservations.size()),
                        ratio(cancelledReservations, periodReservations.size())
                ),
                buildTrend(period, periodReservations, confirmedRevenueByReservation, incidentCountByReservation),
                buildStatusBreakdown(periodReservations),
                topWarehouses,
                topWarehouses.isEmpty() ? null : topWarehouses.get(0),
                buildCityRanking(periodReservations, confirmedRevenueByReservation, incidentCountByReservation),
                buildCourierRanking(periodDeliveries),
                buildOperatorRanking(periodDeliveries),
                totalUsers,
                totalWarehouses,
                activeReservations,
                openIncidents,
                confirmedPayments
        );
    }

    private Map<Long, BigDecimal> resolveConfirmedRevenueByReservation(List<PaymentAttempt> payments) {
        Map<Long, PaymentAttempt> latestConfirmedByReservation = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.CONFIRMED)
                .collect(Collectors.toMap(
                        payment -> payment.getReservation().getId(),
                        Function.identity(),
                        (left, right) -> {
                            Instant leftCreatedAt = left.getCreatedAt();
                            Instant rightCreatedAt = right.getCreatedAt();
                            if (leftCreatedAt == null) {
                                return right;
                            }
                            if (rightCreatedAt == null) {
                                return left;
                            }
                            return rightCreatedAt.isAfter(leftCreatedAt) ? right : left;
                        }
                ));
        return latestConfirmedByReservation.values().stream()
                .collect(Collectors.toMap(
                        payment -> payment.getReservation().getId(),
                        PaymentAttempt::getAmount
                ));
    }

    private List<AdminDashboardResponse.TrendPoint> buildTrend(
            DashboardPeriod period,
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        return switch (period) {
            case WEEK, MONTH -> buildDailyTrend(period, reservations, confirmedRevenueByReservation, incidentCountByReservation);
            case YEAR -> buildMonthlyTrend(period, reservations, confirmedRevenueByReservation, incidentCountByReservation);
        };
    }

    private List<AdminDashboardResponse.TrendPoint> buildDailyTrend(
            DashboardPeriod period,
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        LocalDate endDate = LocalDate.now(REPORT_ZONE);
        int days = period == DashboardPeriod.WEEK ? 7 : 30;
        Map<LocalDate, TrendAccumulator> buckets = new LinkedHashMap<>();
        for (int index = days - 1; index >= 0; index--) {
            LocalDate date = endDate.minusDays(index);
            buckets.put(date, new TrendAccumulator(period.trendLabel(date)));
        }

        for (Reservation reservation : reservations) {
            LocalDate bucketDate = reservation.getStartAt().atZone(REPORT_ZONE).toLocalDate();
            TrendAccumulator accumulator = buckets.get(bucketDate);
            if (accumulator == null) {
                continue;
            }
            accumulator.reservations += 1;
            accumulator.incidents += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }

        return buckets.values().stream()
                .map(TrendAccumulator::toResponse)
                .toList();
    }

    private List<AdminDashboardResponse.TrendPoint> buildMonthlyTrend(
            DashboardPeriod period,
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        YearMonth currentMonth = YearMonth.now(REPORT_ZONE);
        Map<YearMonth, TrendAccumulator> buckets = new LinkedHashMap<>();
        for (int index = 11; index >= 0; index--) {
            YearMonth month = currentMonth.minusMonths(index);
            buckets.put(month, new TrendAccumulator(period.trendLabel(month.atDay(1))));
        }

        for (Reservation reservation : reservations) {
            YearMonth bucketMonth = YearMonth.from(reservation.getStartAt().atZone(REPORT_ZONE));
            TrendAccumulator accumulator = buckets.get(bucketMonth);
            if (accumulator == null) {
                continue;
            }
            accumulator.reservations += 1;
            accumulator.incidents += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }

        return buckets.values().stream()
                .map(TrendAccumulator::toResponse)
                .toList();
    }

    private List<AdminDashboardResponse.StatusBreakdown> buildStatusBreakdown(List<Reservation> reservations) {
        return reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getStatus, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<ReservationStatus, Long>comparingByValue().reversed())
                .map(entry -> new AdminDashboardResponse.StatusBreakdown(
                        entry.getKey().name(),
                        statusLabel(entry.getKey()),
                        entry.getValue()
                ))
                .toList();
    }

    private List<AdminDashboardResponse.WarehousePerformance> buildWarehouseRanking(
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        Map<Long, WarehouseAccumulator> accumulators = new HashMap<>();
        for (Reservation reservation : reservations) {
            Long warehouseId = reservation.getWarehouse().getId();
            WarehouseAccumulator accumulator = accumulators.computeIfAbsent(
                    warehouseId,
                    ignored -> new WarehouseAccumulator(
                            warehouseId,
                            reservation.getWarehouse().getName(),
                            reservation.getWarehouse().getCity().getName(),
                            reservation.getWarehouse().getZone() != null ? reservation.getWarehouse().getZone().getName() : "-"
                    )
            );
            accumulator.interactionCount += 1;
            if (reservation.getStatus() == ReservationStatus.COMPLETED) {
                accumulator.completedReservations += 1;
            }
            if (reservation.getStatus() == ReservationStatus.CANCELLED) {
                accumulator.cancelledReservations += 1;
            }
            accumulator.incidentCount += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }
        return accumulators.values().stream()
                .map(WarehouseAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.WarehousePerformance::interactionCount).reversed()
                        .thenComparing(AdminDashboardResponse.WarehousePerformance::confirmedRevenue, Comparator.reverseOrder())
                        .thenComparing(AdminDashboardResponse.WarehousePerformance::warehouseName))
                .limit(6)
                .toList();
    }

    private List<AdminDashboardResponse.CityPerformance> buildCityRanking(
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        Map<String, CityAccumulator> accumulators = new HashMap<>();
        for (Reservation reservation : reservations) {
            String cityName = reservation.getWarehouse().getCity().getName();
            CityAccumulator accumulator = accumulators.computeIfAbsent(cityName, CityAccumulator::new);
            accumulator.interactionCount += 1;
            if (reservation.getStatus() == ReservationStatus.COMPLETED) {
                accumulator.completedReservations += 1;
            }
            accumulator.incidentCount += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }
        return accumulators.values().stream()
                .map(CityAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.CityPerformance::interactionCount).reversed()
                        .thenComparing(AdminDashboardResponse.CityPerformance::confirmedRevenue, Comparator.reverseOrder())
                        .thenComparing(AdminDashboardResponse.CityPerformance::city))
                .limit(6)
                .toList();
    }

    private List<AdminDashboardResponse.OperationalUserPerformance> buildCourierRanking(List<DeliveryOrder> deliveries) {
        Map<Long, UserOperationalAccumulator> accumulators = new HashMap<>();
        for (DeliveryOrder delivery : deliveries) {
            User courier = delivery.getAssignedCourier();
            if (courier == null || !courier.getRoles().contains(Role.COURIER)) {
                continue;
            }
            UserOperationalAccumulator accumulator = accumulators.computeIfAbsent(
                    courier.getId(),
                    ignored -> new UserOperationalAccumulator(courier.getId(), courier.getFullName(), courier.getEmail())
            );
            accumulator.deliveryAssignedCount += 1;
            if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
                accumulator.deliveryCompletedCount += 1;
            }
            if (ACTIVE_DELIVERY_STATUSES.contains(delivery.getStatus())) {
                accumulator.activeDeliveryCount += 1;
            }
        }
        return accumulators.values().stream()
                .map(UserOperationalAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCompletedCount).reversed()
                        .thenComparingLong(AdminDashboardResponse.OperationalUserPerformance::activeDeliveryCount).reversed()
                        .thenComparing(AdminDashboardResponse.OperationalUserPerformance::fullName))
                .limit(6)
                .toList();
    }

    private List<AdminDashboardResponse.OperationalUserPerformance> buildOperatorRanking(List<DeliveryOrder> deliveries) {
        Map<String, User> usersByEmail = userRepository.findActiveByAnyRole(Set.of(Role.OPERATOR)).stream()
                .filter(user -> user.getEmail() != null)
                .collect(Collectors.toMap(
                        user -> user.getEmail().trim().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<Long, UserOperationalAccumulator> accumulators = new HashMap<>();
        for (DeliveryOrder delivery : deliveries) {
            String createdBy = delivery.getCreatedBy();
            if (createdBy == null || createdBy.isBlank()) {
                continue;
            }
            User operator = usersByEmail.get(createdBy.trim().toLowerCase(Locale.ROOT));
            if (operator == null || !operator.getRoles().contains(Role.OPERATOR)) {
                continue;
            }
            UserOperationalAccumulator accumulator = accumulators.computeIfAbsent(
                    operator.getId(),
                    ignored -> new UserOperationalAccumulator(operator.getId(), operator.getFullName(), operator.getEmail())
            );
            accumulator.deliveryCreatedCount += 1;
            if (ACTIVE_DELIVERY_STATUSES.contains(delivery.getStatus())) {
                accumulator.activeDeliveryCount += 1;
            }
            if (delivery.getAssignedCourier() != null) {
                accumulator.deliveryAssignedCount += 1;
            }
            if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
                accumulator.deliveryCompletedCount += 1;
            }
        }
        return accumulators.values().stream()
                .map(UserOperationalAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCreatedCount).reversed()
                        .thenComparingLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCompletedCount).reversed()
                        .thenComparing(AdminDashboardResponse.OperationalUserPerformance::fullName))
                .limit(6)
                .toList();
    }

    private boolean isActiveReservation(Reservation reservation) {
        return reservation.getStatus() != ReservationStatus.CANCELLED
                && reservation.getStatus() != ReservationStatus.COMPLETED
                && reservation.getStatus() != ReservationStatus.EXPIRED;
    }

    private static final Set<DeliveryStatus> ACTIVE_DELIVERY_STATUSES = Set.of(
            DeliveryStatus.REQUESTED,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.IN_TRANSIT
    );

    private static final Set<ReservationStatus> INACTIVE_RESERVATION_STATUSES = Set.of(
            ReservationStatus.CANCELLED,
            ReservationStatus.COMPLETED,
            ReservationStatus.EXPIRED
    );

    private double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String statusLabel(ReservationStatus status) {
        return switch (status) {
            case DRAFT -> "Borrador";
            case PENDING_PAYMENT -> "Pendiente pago";
            case CONFIRMED -> "Confirmada";
            case CHECKIN_PENDING -> "Check-in pendiente";
            case STORED -> "Almacenado";
            case READY_FOR_PICKUP -> "Listo para recojo";
            case OUT_FOR_DELIVERY -> "En delivery";
            case INCIDENT -> "Incidencia";
            case COMPLETED -> "Completada";
            case CANCELLED -> "Cancelada";
            case EXPIRED -> "Expirada";
        };
    }

    private static final class TrendAccumulator {
        private final String label;
        private long reservations;
        private long incidents;
        private BigDecimal confirmedRevenue = BigDecimal.ZERO;

        private TrendAccumulator(String label) {
            this.label = label;
        }

        private AdminDashboardResponse.TrendPoint toResponse() {
            return new AdminDashboardResponse.TrendPoint(label, reservations, incidents, confirmedRevenue);
        }
    }

    private static final class WarehouseAccumulator {
        private final Long warehouseId;
        private final String warehouseName;
        private final String city;
        private final String zone;
        private long interactionCount;
        private long completedReservations;
        private long cancelledReservations;
        private long incidentCount;
        private BigDecimal confirmedRevenue = BigDecimal.ZERO;

        private WarehouseAccumulator(Long warehouseId, String warehouseName, String city, String zone) {
            this.warehouseId = warehouseId;
            this.warehouseName = warehouseName;
            this.city = city;
            this.zone = zone;
        }

        private AdminDashboardResponse.WarehousePerformance toResponse() {
            return new AdminDashboardResponse.WarehousePerformance(
                    warehouseId,
                    warehouseName,
                    city,
                    zone,
                    interactionCount,
                    completedReservations,
                    cancelledReservations,
                    incidentCount,
                    confirmedRevenue
            );
        }
    }

    private static final class CityAccumulator {
        private final String city;
        private long interactionCount;
        private long completedReservations;
        private long incidentCount;
        private BigDecimal confirmedRevenue = BigDecimal.ZERO;

        private CityAccumulator(String city) {
            this.city = city;
        }

        private AdminDashboardResponse.CityPerformance toResponse() {
            return new AdminDashboardResponse.CityPerformance(
                    city,
                    interactionCount,
                    completedReservations,
                    incidentCount,
                    confirmedRevenue
            );
        }
    }

    private static final class UserOperationalAccumulator {
        private final Long userId;
        private final String fullName;
        private final String email;
        private long deliveryCreatedCount;
        private long deliveryAssignedCount;
        private long deliveryCompletedCount;
        private long activeDeliveryCount;

        private UserOperationalAccumulator(Long userId, String fullName, String email) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
        }

        private AdminDashboardResponse.OperationalUserPerformance toResponse() {
            return new AdminDashboardResponse.OperationalUserPerformance(
                    userId,
                    fullName,
                    email,
                    deliveryCreatedCount,
                    deliveryAssignedCount,
                    deliveryCompletedCount,
                    activeDeliveryCount
            );
        }
    }

    private List<AdminRankingsResponse.RankingItem> buildWarehouseRankingItems(
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation,
            int limit
    ) {
        Map<Long, WarehouseAccumulator> accumulators = new HashMap<>();
        for (Reservation reservation : reservations) {
            Long warehouseId = reservation.getWarehouse().getId();
            WarehouseAccumulator accumulator = accumulators.computeIfAbsent(
                    warehouseId,
                    ignored -> new WarehouseAccumulator(
                            warehouseId,
                            reservation.getWarehouse().getName(),
                            reservation.getWarehouse().getCity().getName(),
                            reservation.getWarehouse().getZone() != null ? reservation.getWarehouse().getZone().getName() : "-"
                    )
            );
            accumulator.interactionCount += 1;
            if (reservation.getStatus() == ReservationStatus.COMPLETED) {
                accumulator.completedReservations += 1;
            }
            if (reservation.getStatus() == ReservationStatus.CANCELLED) {
                accumulator.cancelledReservations += 1;
            }
            accumulator.incidentCount += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }
        
        List<AdminDashboardResponse.WarehousePerformance> sorted = accumulators.values().stream()
                .map(WarehouseAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.WarehousePerformance::interactionCount).reversed()
                        .thenComparing(AdminDashboardResponse.WarehousePerformance::confirmedRevenue, Comparator.reverseOrder())
                        .thenComparing(AdminDashboardResponse.WarehousePerformance::warehouseName))
                .limit(limit)
                .toList();
        
        long totalInteractions = sorted.stream().mapToLong(AdminDashboardResponse.WarehousePerformance::interactionCount).sum();
        
        int rank = 1;
        List<AdminRankingsResponse.RankingItem> result = new java.util.ArrayList<>();
        for (AdminDashboardResponse.WarehousePerformance item : sorted) {
            double percentage = totalInteractions > 0 ? (item.interactionCount() * 100.0 / totalInteractions) : 0;
            result.add(new AdminRankingsResponse.RankingItem(
                    rank++,
                    String.valueOf(item.warehouseId()),
                    item.warehouseName(),
                    item.interactionCount(),
                    percentage
            ));
        }
        return result;
    }

    private List<AdminRankingsResponse.RankingItem> buildCityRankingItems(
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation,
            int limit
    ) {
        Map<String, CityAccumulator> accumulators = new HashMap<>();
        for (Reservation reservation : reservations) {
            String cityName = reservation.getWarehouse().getCity().getName();
            CityAccumulator accumulator = accumulators.computeIfAbsent(cityName, CityAccumulator::new);
            accumulator.interactionCount += 1;
            if (reservation.getStatus() == ReservationStatus.COMPLETED) {
                accumulator.completedReservations += 1;
            }
            accumulator.incidentCount += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }
        
        List<AdminDashboardResponse.CityPerformance> sorted = accumulators.values().stream()
                .map(CityAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.CityPerformance::interactionCount).reversed()
                        .thenComparing(AdminDashboardResponse.CityPerformance::confirmedRevenue, Comparator.reverseOrder())
                        .thenComparing(AdminDashboardResponse.CityPerformance::city))
                .limit(limit)
                .toList();
        
        long totalInteractions = sorted.stream().mapToLong(AdminDashboardResponse.CityPerformance::interactionCount).sum();
        
        int rank = 1;
        List<AdminRankingsResponse.RankingItem> result = new java.util.ArrayList<>();
        for (AdminDashboardResponse.CityPerformance item : sorted) {
            double percentage = totalInteractions > 0 ? (item.interactionCount() * 100.0 / totalInteractions) : 0;
            result.add(new AdminRankingsResponse.RankingItem(
                    rank++,
                    item.city(),
                    item.city(),
                    item.interactionCount(),
                    percentage
            ));
        }
        return result;
    }

    private List<AdminRankingsResponse.RankingItem> buildCourierRankingItems(
            List<DeliveryOrder> deliveries,
            int limit
    ) {
        Map<Long, UserOperationalAccumulator> accumulators = new HashMap<>();
        for (DeliveryOrder delivery : deliveries) {
            User courier = delivery.getAssignedCourier();
            if (courier == null || !courier.getRoles().contains(Role.COURIER)) {
                continue;
            }
            UserOperationalAccumulator accumulator = accumulators.computeIfAbsent(
                    courier.getId(),
                    ignored -> new UserOperationalAccumulator(courier.getId(), courier.getFullName(), courier.getEmail())
            );
            accumulator.deliveryAssignedCount += 1;
            if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
                accumulator.deliveryCompletedCount += 1;
            }
            if (ACTIVE_DELIVERY_STATUSES.contains(delivery.getStatus())) {
                accumulator.activeDeliveryCount += 1;
            }
        }
        
        List<AdminDashboardResponse.OperationalUserPerformance> sorted = accumulators.values().stream()
                .map(UserOperationalAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCompletedCount).reversed()
                        .thenComparingLong(AdminDashboardResponse.OperationalUserPerformance::activeDeliveryCount).reversed()
                        .thenComparing(AdminDashboardResponse.OperationalUserPerformance::fullName))
                .limit(limit)
                .toList();
        
        long totalDeliveries = sorted.stream().mapToLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCompletedCount).sum();
        
        int rank = 1;
        List<AdminRankingsResponse.RankingItem> result = new java.util.ArrayList<>();
        for (AdminDashboardResponse.OperationalUserPerformance item : sorted) {
            double percentage = totalDeliveries > 0 ? (item.deliveryCompletedCount() * 100.0 / totalDeliveries) : 0;
            result.add(new AdminRankingsResponse.RankingItem(
                    rank++,
                    String.valueOf(item.userId()),
                    item.fullName(),
                    item.deliveryCompletedCount(),
                    percentage
            ));
        }
        return result;
    }

    private List<AdminRankingsResponse.RankingItem> buildOperatorRankingItems(
            List<DeliveryOrder> deliveries,
            int limit
    ) {
        Map<String, User> usersByEmail = userRepository.findActiveByAnyRole(Set.of(Role.OPERATOR)).stream()
                .filter(user -> user.getEmail() != null)
                .collect(Collectors.toMap(
                        user -> user.getEmail().trim().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<Long, UserOperationalAccumulator> accumulators = new HashMap<>();
        for (DeliveryOrder delivery : deliveries) {
            String createdBy = delivery.getCreatedBy();
            if (createdBy == null || createdBy.isBlank()) {
                continue;
            }
            User operator = usersByEmail.get(createdBy.trim().toLowerCase(Locale.ROOT));
            if (operator == null || !operator.getRoles().contains(Role.OPERATOR)) {
                continue;
            }
            UserOperationalAccumulator accumulator = accumulators.computeIfAbsent(
                    operator.getId(),
                    ignored -> new UserOperationalAccumulator(operator.getId(), operator.getFullName(), operator.getEmail())
            );
            accumulator.deliveryCreatedCount += 1;
            if (ACTIVE_DELIVERY_STATUSES.contains(delivery.getStatus())) {
                accumulator.activeDeliveryCount += 1;
            }
            if (delivery.getAssignedCourier() != null) {
                accumulator.deliveryAssignedCount += 1;
            }
            if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
                accumulator.deliveryCompletedCount += 1;
            }
        }
        
        List<AdminDashboardResponse.OperationalUserPerformance> sorted = accumulators.values().stream()
                .map(UserOperationalAccumulator::toResponse)
                .sorted(Comparator
                        .comparingLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCreatedCount).reversed()
                        .thenComparingLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCompletedCount).reversed()
                        .thenComparing(AdminDashboardResponse.OperationalUserPerformance::fullName))
                .limit(limit)
                .toList();
        
        long totalCreated = sorted.stream().mapToLong(AdminDashboardResponse.OperationalUserPerformance::deliveryCreatedCount).sum();
        
        int rank = 1;
        List<AdminRankingsResponse.RankingItem> result = new java.util.ArrayList<>();
        for (AdminDashboardResponse.OperationalUserPerformance item : sorted) {
            double percentage = totalCreated > 0 ? (item.deliveryCreatedCount() * 100.0 / totalCreated) : 0;
            result.add(new AdminRankingsResponse.RankingItem(
                    rank++,
                    String.valueOf(item.userId()),
                    item.fullName(),
                    item.deliveryCreatedCount(),
                    percentage
            ));
        }
        return result;
    }

    private List<AdminTrendsResponse.TrendPoint> buildTrendItems(
            DashboardPeriod period,
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        return switch (period) {
            case WEEK, MONTH -> buildDailyTrendItems(period, reservations, confirmedRevenueByReservation, incidentCountByReservation);
            case YEAR -> buildMonthlyTrendItems(period, reservations, confirmedRevenueByReservation, incidentCountByReservation);
        };
    }

    private List<AdminTrendsResponse.TrendPoint> buildDailyTrendItems(
            DashboardPeriod period,
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        LocalDate endDate = LocalDate.now(REPORT_ZONE);
        int days = period == DashboardPeriod.WEEK ? 7 : 30;
        Map<LocalDate, TrendAccumulator> buckets = new LinkedHashMap<>();
        for (int index = days - 1; index >= 0; index--) {
            LocalDate date = endDate.minusDays(index);
            buckets.put(date, new TrendAccumulator(period.trendLabel(date)));
        }

        for (Reservation reservation : reservations) {
            LocalDate bucketDate = reservation.getStartAt().atZone(REPORT_ZONE).toLocalDate();
            TrendAccumulator accumulator = buckets.get(bucketDate);
            if (accumulator == null) {
                continue;
            }
            accumulator.reservations += 1;
            accumulator.incidents += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }

        return buckets.values().stream()
                .map(acc -> new AdminTrendsResponse.TrendPoint(acc.label, acc.reservations, acc.incidents, acc.confirmedRevenue))
                .toList();
    }

    private List<AdminTrendsResponse.TrendPoint> buildMonthlyTrendItems(
            DashboardPeriod period,
            List<Reservation> reservations,
            Map<Long, BigDecimal> confirmedRevenueByReservation,
            Map<Long, Long> incidentCountByReservation
    ) {
        YearMonth currentMonth = YearMonth.now(REPORT_ZONE);
        Map<YearMonth, TrendAccumulator> buckets = new LinkedHashMap<>();
        for (int index = 11; index >= 0; index--) {
            YearMonth month = currentMonth.minusMonths(index);
            buckets.put(month, new TrendAccumulator(period.trendLabel(month.atDay(1))));
        }

        for (Reservation reservation : reservations) {
            YearMonth bucketMonth = YearMonth.from(reservation.getStartAt().atZone(REPORT_ZONE));
            TrendAccumulator accumulator = buckets.get(bucketMonth);
            if (accumulator == null) {
                continue;
            }
            accumulator.reservations += 1;
            accumulator.incidents += incidentCountByReservation.getOrDefault(reservation.getId(), 0L);
            accumulator.confirmedRevenue = accumulator.confirmedRevenue.add(
                    confirmedRevenueByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
            );
        }

        return buckets.values().stream()
                .map(acc -> new AdminTrendsResponse.TrendPoint(acc.label, acc.reservations, acc.incidents, acc.confirmedRevenue))
                .toList();
    }

    public void invalidateCache() {
        cache.clear();
        log.info("Dashboard cache invalidated");
    }

    public void invalidateCache(String period) {
        String dashboardKey = "dashboard_" + period;
        String summaryKey = "dashboard_summary_" + period;
        String trendsKey = "dashboard_trends_" + period;
        
        cache.remove(dashboardKey);
        cache.remove(summaryKey);
        cache.remove(trendsKey);
        
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith("dashboard_rankings_" + period));
        
        log.info("Dashboard cache invalidated for period: {}", period);
    }

    public int getCacheSize() {
        return cache.size();
    }

    private static class CacheEntry<T> {
        final T response;
        final long timestamp;

        CacheEntry(T response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}

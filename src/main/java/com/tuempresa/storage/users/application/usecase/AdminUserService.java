package com.tuempresa.storage.users.application.usecase;

import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import com.tuempresa.storage.auth.infrastructure.out.persistence.RefreshTokenRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService.FileCategory;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import com.tuempresa.storage.users.application.dto.AdminUserPagedResponse;
import com.tuempresa.storage.users.application.dto.AdminUserResponse;
import com.tuempresa.storage.users.application.dto.AdminUserSummaryResponse;
import com.tuempresa.storage.users.application.dto.BulkOperationResponse;
import com.tuempresa.storage.users.application.dto.CreateAdminUserRequest;
import com.tuempresa.storage.users.application.dto.UpdateAdminUserRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserActiveRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserPasswordRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserRolesRequest;
import com.tuempresa.storage.users.domain.AuthProvider;
import com.tuempresa.storage.users.domain.DocumentType;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final Set<DeliveryStatus> ACTIVE_DELIVERY_STATUSES = Set.of(
            DeliveryStatus.REQUESTED,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.IN_TRANSIT
    );
    private static final Set<Role> WAREHOUSE_SCOPED_ROLES = Set.of(
            Role.OPERATOR,
            Role.CITY_SUPERVISOR,
            Role.COURIER,
            Role.SUPPORT
    );
    private static final Comparator<User> NEWEST_USER_COMPARATOR = Comparator
            .comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(User::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    private static final Comparator<User> NAME_ASC_COMPARATOR = Comparator
            .comparing(User::getFullName, Comparator.nullsLast(String::compareToIgnoreCase));

    private final UserRepository userRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final com.tuempresa.storage.shared.application.usecase.AuditLogService auditLogService;
    private final com.tuempresa.storage.firebase.application.FirebaseAdminService firebaseAdminService;

    public AdminUserService(
            UserRepository userRepository,
            DeliveryOrderRepository deliveryOrderRepository,
            RefreshTokenRepository refreshTokenRepository,
            WarehouseRepository warehouseRepository,
            PasswordEncoder passwordEncoder,
            StorageService storageService,
            com.tuempresa.storage.shared.application.usecase.AuditLogService auditLogService,
            com.tuempresa.storage.firebase.application.FirebaseAdminService firebaseAdminService
    ) {
        this.userRepository = userRepository;
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.warehouseRepository = warehouseRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.firebaseAdminService = firebaseAdminService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(String query, Role role) {
        return list(query, role, false, null);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(String query, Role role, boolean latestOnly, Integer limit) {
        List<User> filtered = findFilteredUsers(query, role);

        List<User> usersToReturn;
        if (latestOnly) {
            int maxItems = (limit == null || limit <= 0) ? 1 : limit;
            usersToReturn = filtered.stream()
                    .sorted(NEWEST_USER_COMPARATOR)
                    .limit(maxItems)
                    .toList();
        } else if (limit != null && limit > 0) {
            usersToReturn = filtered.stream()
                    .sorted(NEWEST_USER_COMPARATOR)
                    .limit(limit)
                    .toList();
        } else {
            usersToReturn = filtered.stream()
                    .sorted(NAME_ASC_COMPARATOR)
                    .toList();
        }

        UserMetrics metrics = buildMetricsForUsers(usersToReturn);
        return usersToReturn.stream()
                .map(user -> toResponse(user, metrics))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserPagedResponse> listPage(
            String query,
            Role role,
            int page,
            int size
    ) {
        String normalizedQuery = normalizeQuery(query);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<User> userPage = userRepository.searchAdminPage(
                normalizedQuery == null ? "" : normalizedQuery,
                role,
                pageRequest
        );

        List<AdminUserPagedResponse> content = userPage.getContent().stream()
                .map(user -> new AdminUserPagedResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRoles().stream()
                                .map(Role::name)
                                .sorted()
                                .collect(Collectors.joining(",")),
                        user.isActive(),
                        user.getWarehouseAssignments().stream()
                                .map(Warehouse::getId)
                                .sorted()
                                .toList(),
                        user.getCreatedAt()
                ))
                .toList();

        return new PagedResponse<>(
                content,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.hasNext(),
                userPage.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserSummaryResponse summary(String query, Role role) {
        List<User> filtered = findFilteredUsers(query, role);
        UserMetrics metrics = buildMetricsForUsers(filtered);
        long totalUsers = filtered.size();
        long activeUsers = filtered.stream().filter(User::isActive).count();
        long operatorUsers = filtered.stream().filter(user -> user.getRoles().contains(Role.OPERATOR)).count();
        long courierUsers = filtered.stream().filter(user -> user.getRoles().contains(Role.COURIER)).count();
        long completedDeliveries = filtered.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .mapToLong(userId -> metrics.completedCountByUserId().getOrDefault(userId, 0L))
                .sum();
        return new AdminUserSummaryResponse(
                totalUsers,
                activeUsers,
                operatorUsers,
                courierUsers,
                completedDeliveries
        );
    }

    @Transactional
    public AdminUserResponse create(CreateAdminUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EMAIL_ALREADY_EXISTS", "El correo ya existe.");
        }
        validateCourierPlate(request.roles(), request.vehiclePlate());
        validateAdminCannotBeDisabled(request.roles(), request.active());
        NameParts names = NameParts.from(request.fullName());
        User user = User.of(
                request.fullName(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.phone(),
                request.roles()
        );
        user.applyRegistrationDetails(
                names.firstName(),
                names.lastName(),
                defaultText(request.nationality(), "Peru"),
                defaultText(request.preferredLanguage(), "es"),
                request.phone(),
                true,
                null
        );
        user.markEmailVerified();
        user.setActive(request.active() == null || request.active());
        user.markManagedByAdmin(true);
        user.updateVehiclePlate(resolveVehiclePlate(request.roles(), request.vehiclePlate()));
        user.updateProfile(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                normalizeText(request.documentPhotoPath()),
                null,
                null,
                null,
                parseDocumentType(request.documentType()),
                normalizeText(request.documentNumber()),
                null,
                null,
                null,
                null
        );
        if (request.warehouseIds() != null || request.roles().stream().noneMatch(WAREHOUSE_SCOPED_ROLES::contains)) {
            user.updateWarehouseAssignments(resolveWarehouseAssignments(request.roles(), request.warehouseIds()));
        }
        User saved = userRepository.save(user);
        saved = syncFirebase(saved, request.password());
        return toResponse(saved, buildMetricsForUsers(List.of(saved)));
    }

    @Transactional
    public AdminUserResponse update(Long id, UpdateAdminUserRequest request) {
        User user = requireUser(id);
        String normalizedEmail = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "USER_EMAIL_ALREADY_EXISTS", "El correo ya existe.");
                });
        NameParts names = NameParts.from(request.fullName());
        user.updateAdminProfile(
                names.firstName(),
                names.lastName(),
                normalizedEmail,
                request.phone(),
                defaultText(request.nationality(), user.getNationality() == null ? "Peru" : user.getNationality()),
                defaultText(request.preferredLanguage(), user.getPreferredLanguage() == null ? "es" : user.getPreferredLanguage())
        );
        validateCourierPlate(request.roles(), request.vehiclePlate());
        validateAdminCannotBeDisabled(request.roles(), request.active());
        user.updateRoles(request.roles());
        user.markManagedByAdmin(true);
        user.updateVehiclePlate(resolveVehiclePlate(request.roles(), request.vehiclePlate()));
        user.updateProfile(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                normalizeText(request.documentPhotoPath()),
                null,
                null,
                null,
                parseDocumentType(request.documentType()),
                normalizeText(request.documentNumber()),
                null,
                null,
                null,
                null
        );
        if (request.active() != null) {
            user.setActive(request.active());
        }
        user.updateWarehouseAssignments(resolveWarehouseAssignments(request.roles(), request.warehouseIds()));
        User saved = userRepository.save(user);
        saved = syncFirebase(saved, null);
        return toResponse(saved, buildMetricsForUsers(List.of(saved)));
    }

    @Transactional
    public AdminUserResponse updateRoles(Long id, UpdateUserRolesRequest request) {
        User user = requireUser(id);
        user.updateRoles(request.roles());
        if (request.roles() == null || !request.roles().contains(Role.COURIER)) {
            user.updateVehiclePlate(null);
        }
        if (request.warehouseIds() != null) {
            user.updateWarehouseAssignments(resolveWarehouseAssignments(request.roles(), request.warehouseIds()));
        }
        User saved = userRepository.save(user);
        saved = syncFirebase(saved, null);
        return toResponse(saved, buildMetricsForUsers(List.of(saved)));
    }

    @Transactional
    public AdminUserResponse updateActive(Long id, UpdateUserActiveRequest request) {
        User user = requireUser(id);
        if (!request.active() && user.getRoles().contains(Role.ADMIN)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_USER_CANNOT_BE_DISABLED",
                    "Un usuario ADMIN no puede quedar inactivo."
            );
        }
        user.setActive(request.active());
        User saved = userRepository.save(user);
        saved = syncFirebase(saved, null);
        return toResponse(saved, buildMetricsForUsers(List.of(saved)));
    }

    @Transactional
    public AdminUserResponse updatePassword(Long id, UpdateUserPasswordRequest request) {
        User user = requireUser(id);
        user.updatePasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        saved = syncFirebase(saved, request.password());
        return toResponse(saved, buildMetricsForUsers(List.of(saved)));
    }

    @Transactional
    public void delete(Long id, AuthUserPrincipal principal) {
        User user = requireUser(id);
        String firebaseUid = user.getFirebaseUid();
        if (user.getRoles().contains(Role.ADMIN)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_USER_DELETE_FORBIDDEN",
                    "Un usuario ADMIN solo puede ser editado."
            );
        }
        if (principal != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(principal.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DELETE_SELF_FORBIDDEN", "No puedes eliminar tu propio usuario.");
        }
        try {
            refreshTokenRepository.deleteAllByUserId(user.getId());
            userRepository.delete(user);
            userRepository.flush();
            firebaseAdminService.deleteUserAccount(firebaseUid);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_DELETE_CONFLICT",
                    "No se puede eliminar el usuario porque ya tiene operaciones relacionadas."
            );
        }
    }

    @Transactional
    public void deleteFirebaseAccountByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_EMAIL_REQUIRED", "Debes enviar un correo valido.");
        }
        firebaseAdminService.deleteUserAccountByEmail(normalizedEmail);
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            user.linkFirebaseIdentity(AuthProvider.LOCAL, null);
            userRepository.save(user);
        });
    }

    @Transactional
    public BulkOperationResponse bulkDelete(Set<Long> ids, AuthUserPrincipal principal) {
        int processed = ids.size();
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                delete(id, principal);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        if (failed > 0) {
            return BulkOperationResponse.partial(processed, succeeded, failed, "Eliminacion bulk");
        }
        return BulkOperationResponse.success(processed, "Eliminacion bulk");
    }

    @Transactional
    public BulkOperationResponse bulkUpdateActive(Set<Long> ids, boolean active, AuthUserPrincipal principal) {
        int processed = ids.size();
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                User user = userRepository.findById(id).orElseThrow(() -> 
                        new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado: " + id));
                if (!active && user.getRoles().contains(Role.ADMIN)) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "ADMIN_USER_CANNOT_BE_DISABLED",
                            "Un usuario ADMIN no puede quedar inactivo: " + id
                    );
                }
                user.setActive(active);
                userRepository.save(user);
                syncFirebase(user, null);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        if (failed > 0) {
            return BulkOperationResponse.partial(processed, succeeded, failed, "Actualizacion bulk de estado");
        }
        return BulkOperationResponse.success(processed, "Actualizacion bulk de estado");
    }

    @Transactional
    public BulkOperationResponse bulkUpdateRoles(Set<Long> ids, Set<Role> roles, Set<Long> warehouseIds, AuthUserPrincipal principal) {
        int processed = ids.size();
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                User user = userRepository.findById(id).orElseThrow(() -> 
                        new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado: " + id));
                validateCourierPlate(roles, null);
                validateAdminCannotBeDisabled(roles, null);
                user.updateRoles(roles);
                if (roles == null || !roles.contains(Role.COURIER)) {
                    user.updateVehiclePlate(null);
                }
                user.updateWarehouseAssignments(resolveWarehouseAssignments(roles, warehouseIds));
                userRepository.save(user);
                syncFirebase(user, null);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        if (failed > 0) {
            return BulkOperationResponse.partial(processed, succeeded, failed, "Actualizacion bulk de roles");
        }
        return BulkOperationResponse.success(processed, "Actualizacion bulk de roles");
    }

    @Transactional
    public String uploadDocumentPhoto(MultipartFile file) throws Exception {
        StorageService.UploadResult result = storageService.upload(file, FileCategory.DOCUMENTS);
        auditLogService.logFileUpload(result.filename(), "documents", "admin-document-upload");
        return result.url();
    }

    @Transactional(readOnly = true)
    public List<UserExportRow> exportUsers(String query, Role role) {
        List<User> users = findFilteredUsers(query, role);
        UserMetrics metrics = buildMetricsForUsers(users);
        return users.stream()
                .map(user -> toExportRow(user, metrics))
                .toList();
    }

    private UserExportRow toExportRow(User user, UserMetrics metrics) {
        String roles = user.getRoles().stream()
                .map(Role::name)
                .sorted()
                .collect(Collectors.joining(";"));
        String warehouses = user.getWarehouseAssignments().stream()
                .map(Warehouse::getName)
                .sorted()
                .collect(Collectors.joining(";"));
        long assignedCount = metrics.assignedCountByUserId().getOrDefault(user.getId(), 0L);
        long completedCount = metrics.completedCountByUserId().getOrDefault(user.getId(), 0L);
        return new UserExportRow(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getNationality(),
                roles,
                warehouses,
                user.isActive(),
                user.isEmailVerified(),
                user.isProfileCompleted(),
                assignedCount,
                completedCount,
                user.getCreatedAt()
        );
    }

    public record UserExportRow(
            Long id,
            String fullName,
            String email,
            String phone,
            String nationality,
            String roles,
            String warehouses,
            boolean active,
            boolean emailVerified,
            boolean profileCompleted,
            long assignedDeliveries,
            long completedDeliveries,
            Instant createdAt
    ) {}

    private User syncFirebase(User user, String rawPassword) {
        String firebaseUid = firebaseAdminService.syncUserAccount(user, rawPassword);
        if (firebaseUid != null && !firebaseUid.equals(user.getFirebaseUid())) {
            user.linkFirebaseIdentity(user.getAuthProvider(), firebaseUid);
            user = userRepository.save(user);
        }
        firebaseAdminService.mirrorClientProfile(user);
        return user;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesQuery(User user, String query) {
        return contains(user.getFullName(), query)
                || contains(user.getEmail(), query)
                || contains(user.getPhone(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private List<User> findFilteredUsers(String query, Role role) {
        String normalizedQuery = normalizeQuery(query);
        return userRepository.findAllWithRolesAndWarehouseAssignments()
                .stream()
                .filter(user -> normalizedQuery == null || matchesQuery(user, normalizedQuery))
                .filter(user -> role == null || user.getRoles().contains(role))
                .toList();
    }

    private AdminUserResponse toResponse(User user, UserMetrics metrics) {
        List<String> roles = user.getRoles()
                .stream()
                .map(Role::name)
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Long> warehouseIds = user.getWarehouseAssignments().stream()
                .map(Warehouse::getId)
                .sorted()
                .toList();
        List<String> warehouseNames = user.getWarehouseAssignments().stream()
                .map(Warehouse::getName)
                .sorted(String::compareToIgnoreCase)
                .toList();
        long assignedCount = metrics.assignedCountByUserId().getOrDefault(user.getId(), 0L);
        long completedCount = metrics.completedCountByUserId().getOrDefault(user.getId(), 0L);
        long activeCount = metrics.activeCountByUserId().getOrDefault(user.getId(), 0L);
        String normalizedEmail = normalizeEmail(user.getEmail());
        long createdCount = normalizedEmail == null
                ? 0L
                : metrics.createdCountByUsername().getOrDefault(normalizedEmail, 0L);
        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getNationality(),
                user.getPreferredLanguage(),
                user.getAuthProvider().name(),
                user.isManagedByAdmin(),
                user.getPrimaryDocumentType() == null ? null : user.getPrimaryDocumentType().name(),
                user.getPrimaryDocumentNumber(),
                user.getProfilePhotoPath(),
                user.getVehiclePlate(),
                user.isEmailVerified(),
                user.isProfileCompleted(),
                user.isActive(),
                roles,
                warehouseIds,
                warehouseNames,
                createdCount,
                assignedCount,
                completedCount,
                activeCount,
                user.getCreatedAt()
        );
    }

    private Set<Warehouse> resolveWarehouseAssignments(Set<Role> roles, Set<Long> warehouseIds) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        boolean requiresScope = roles.stream().anyMatch(WAREHOUSE_SCOPED_ROLES::contains);
        if (!requiresScope) {
            return Set.of();
        }
        Set<Long> normalizedIds = warehouseIds == null
                ? Set.of()
                : warehouseIds.stream()
                        .filter(id -> id != null && id > 0)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedIds.isEmpty()) {
            return Set.of();
        }
        List<Warehouse> warehouses = warehouseRepository.findAllById(normalizedIds);
        if (warehouses.size() != normalizedIds.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WAREHOUSE_ASSIGNMENT_INVALID",
                    "Una o mas sedes asignadas no existen."
            );
        }
        return new LinkedHashSet<>(warehouses);
    }

    private void validateCourierPlate(Set<Role> roles, String vehiclePlate) {
        if (roles != null && roles.contains(Role.COURIER) && (vehiclePlate == null || vehiclePlate.isBlank())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COURIER_VEHICLE_PLATE_REQUIRED",
                    "Debes registrar la placa del vehiculo para un courier."
            );
        }
    }

    private String resolveVehiclePlate(Set<Role> roles, String vehiclePlate) {
        if (roles == null || !roles.contains(Role.COURIER)) {
            return null;
        }
        return vehiclePlate == null ? null : vehiclePlate.trim().toUpperCase(Locale.ROOT);
    }

    private void validateAdminCannotBeDisabled(Set<Role> roles, Boolean active) {
        if (roles != null && roles.contains(Role.ADMIN) && Boolean.FALSE.equals(active)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_USER_CANNOT_BE_DISABLED",
                    "Un usuario ADMIN no puede quedar inactivo."
            );
        }
    }

    private DocumentType parseDocumentType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        DocumentType type = DocumentType.fromNullable(rawValue);
        if (type == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_USER_DOCUMENT_TYPE_INVALID",
                    "Tipo de documento no soportado."
            );
        }
        return type;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private UserMetrics buildMetricsForUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            return UserMetrics.empty();
        }
        Set<Long> userIds = users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> usernames = users.stream()
                .map(User::getEmail)
                .map(this::normalizeEmail)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Long> assignedCountByUserId = userIds.isEmpty()
                ? Map.of()
                : toUserCountMap(deliveryOrderRepository.countAssignedByCourierIds(userIds));
        Map<Long, Long> completedCountByUserId = userIds.isEmpty()
                ? Map.of()
                : toUserCountMap(deliveryOrderRepository.countByCourierIdsAndStatus(userIds, DeliveryStatus.DELIVERED));
        Map<Long, Long> activeCountByUserId = userIds.isEmpty()
                ? Map.of()
                : toUserCountMap(deliveryOrderRepository.countByCourierIdsAndStatuses(userIds, ACTIVE_DELIVERY_STATUSES));
        Map<String, Long> createdCountByUsername = usernames.isEmpty()
                ? Map.of()
                : toCreatorCountMap(deliveryOrderRepository.countByCreatedByIn(usernames));

        return new UserMetrics(
                createdCountByUsername,
                assignedCountByUserId,
                completedCountByUserId,
                activeCountByUserId
        );
    }

    private Map<Long, Long> toUserCountMap(List<DeliveryOrderRepository.UserDeliveryCountProjection> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (DeliveryOrderRepository.UserDeliveryCountProjection row : rows) {
            if (row.getUserId() != null) {
                result.put(row.getUserId(), row.getTotal());
            }
        }
        return result;
    }

    private Map<String, Long> toCreatorCountMap(List<DeliveryOrderRepository.CreatorDeliveryCountProjection> rows) {
        Map<String, Long> result = new HashMap<>();
        for (DeliveryOrderRepository.CreatorDeliveryCountProjection row : rows) {
            if (row.getUsername() != null && !row.getUsername().isBlank()) {
                result.put(row.getUsername().trim().toLowerCase(Locale.ROOT), row.getTotal());
            }
        }
        return result;
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record UserMetrics(
            Map<String, Long> createdCountByUsername,
            Map<Long, Long> assignedCountByUserId,
            Map<Long, Long> completedCountByUserId,
            Map<Long, Long> activeCountByUserId
    ) {
        private static UserMetrics empty() {
            return new UserMetrics(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private record NameParts(String firstName, String lastName) {
        private static NameParts from(String fullName) {
            String normalized = fullName == null ? "" : fullName.trim();
            if (normalized.isEmpty()) {
                return new NameParts(null, null);
            }
            String[] parts = normalized.split("\\s+");
            if (parts.length == 1) {
                return new NameParts(parts[0], parts[0]);
            }
            int middle = Math.max(1, parts.length / 2);
            String first = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, middle));
            String last = String.join(" ", java.util.Arrays.copyOfRange(parts, middle, parts.length));
            return new NameParts(first, last);
        }
    }
}

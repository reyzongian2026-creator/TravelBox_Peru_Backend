package com.tuempresa.storage.users.infrastructure.out.persistence;

import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select distinct u from User u
            join u.roles r
            where u.active = true
              and r in :roles
            """)
    List<User> findActiveByAnyRole(@Param("roles") Collection<Role> roles);

    @Query("""
            select distinct u from User u
            join u.roles r
            join u.warehouseAssignments w
            where u.active = true
              and r in :roles
              and w.id = :warehouseId
            """)
    List<User> findActiveByAnyRoleAndWarehouseId(
            @Param("roles") Collection<Role> roles,
            @Param("warehouseId") Long warehouseId
    );

    @EntityGraph(attributePaths = {"roles", "warehouseAssignments"})
    Optional<User> findWithRolesAndWarehouseAssignmentsById(Long id);

    @EntityGraph(attributePaths = {"roles", "warehouseAssignments"})
    @Query("select distinct u from User u")
    List<User> findAllWithRolesAndWarehouseAssignments();

    @EntityGraph(attributePaths = {"roles", "warehouseAssignments"})
    @Query("""
            select distinct u from User u
            where (:query = '' or lower(u.fullName) like concat('%', lower(:query), '%')
                   or lower(u.email) like concat('%', lower(:query), '%')
                   or lower(u.phone) like concat('%', lower(:query), '%'))
              and (:roleFilter is null or :roleFilter member of u.roles)
            order by u.createdAt desc
            """)
    Page<User> searchAdminPage(
            @Param("query") String query,
            @Param("roleFilter") Role role,
            Pageable pageable
    );
}

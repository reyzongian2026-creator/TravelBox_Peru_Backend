package com.tuempresa.storage.users.infrastructure.out.persistence;

import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.domain.Role;
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
}

package com.tuempresa.storage.payments.infrastructure.out.persistence;

import com.tuempresa.storage.payments.domain.SavedCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedCardRepository extends JpaRepository<SavedCard, Long> {
    List<SavedCard> findByUserIdAndActiveTrueOrderByLastUsedAtDesc(Long userId);
    Optional<SavedCard> findByUserIdAndTokenAndActiveTrue(Long userId, String token);
}

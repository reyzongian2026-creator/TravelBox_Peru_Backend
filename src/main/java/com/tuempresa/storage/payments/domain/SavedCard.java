package com.tuempresa.storage.payments.domain;

import com.tuempresa.storage.users.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "saved_cards")
@Getter
@Setter
@NoArgsConstructor
public class SavedCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String token;

    private String cardAlias; // Ej: Visa **** 1234
    
    private String cardBrand; // Ej: Visa, Mastercard
    
    private String lastFourDigits;
    
    private String expirationMonth;
    
    private String expirationYear;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastUsedAt;
    
    private boolean active = true;

    public static SavedCard of(User user, String token, String alias, String brand, String lastFour, String expMonth, String expYear) {
        SavedCard card = new SavedCard();
        card.setUser(user);
        card.setToken(token);
        card.setCardAlias(alias);
        card.setCardBrand(brand);
        card.setLastFourDigits(lastFour);
        card.setExpirationMonth(expMonth);
        card.setExpirationYear(expYear);
        card.setCreatedAt(Instant.now());
        card.setLastUsedAt(Instant.now());
        return card;
    }
}

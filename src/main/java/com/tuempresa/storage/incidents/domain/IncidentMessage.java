package com.tuempresa.storage.incidents.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import com.tuempresa.storage.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Locale;

@Entity
@Table(name = "incident_messages")
public class IncidentMessage extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "original_language", length = 5)
    private String originalLanguage;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    public static IncidentMessage create(
            Incident incident,
            User author,
            String message,
            String originalLanguage
    ) {
        return create(incident, author, message, originalLanguage, null);
    }

    public static IncidentMessage create(
            Incident incident,
            User author,
            String message,
            String originalLanguage,
            String imageUrl
    ) {
        IncidentMessage item = new IncidentMessage();
        item.incident = incident;
        item.author = author;
        item.message = message == null ? "" : message.trim();
        item.originalLanguage = normalizeLanguage(originalLanguage);
        item.imageUrl = imageUrl;
        return item;
    }

    public Incident getIncident() {
        return incident;
    }

    public User getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    private static String normalizeLanguage(String originalLanguage) {
        if (originalLanguage == null || originalLanguage.isBlank()) {
            return "es";
        }
        String normalized = originalLanguage.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 2 ? normalized : normalized.substring(0, 2);
    }
}

package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.Theme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tour_product")
public class TourProductJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "theme", nullable = false, length = 32)
    private Theme theme;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    protected TourProductJpaEntity() {
    }

    public TourProductJpaEntity(Theme theme, String name, String description) {
        this.theme = Objects.requireNonNull(theme, "theme must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public Long getId() {
        return id;
    }

    public Theme getTheme() {
        return theme;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}

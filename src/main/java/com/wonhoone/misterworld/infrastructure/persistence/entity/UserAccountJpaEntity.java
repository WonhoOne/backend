package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_account",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_account_login_id", columnNames = "login_id"))
public class UserAccountJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 255)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "contact", nullable = false, length = 255)
    private String contact;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole role;

    protected UserAccountJpaEntity() {
    }

    /** Accepts an already encoded hash; password encoding belongs to the Auth use case. */
    public UserAccountJpaEntity(String loginId, String passwordHash, String name,
                                String address, String contact, UserRole role) {
        this.loginId = Objects.requireNonNull(loginId, "loginId must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.contact = Objects.requireNonNull(contact, "contact must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    /** Startup compatibility update only; account identity and credentials are preserved. */
    public void canonicalizeLoginId(String canonicalLoginId) {
        this.loginId = Objects.requireNonNull(canonicalLoginId);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getContact() {
        return contact;
    }

    public UserRole getRole() {
        return role;
    }
}

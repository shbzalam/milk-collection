package com.zenalyst.milkcollection.tanker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A collection vehicle. {@code capacityLitres} is the hard limit enforced server-side on
 * every milk collection.
 */
@Entity
@Table(name = "tanker")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Tanker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tanker_code", nullable = false, unique = true, length = 32)
    private String tankerCode;

    @Column(name = "registration_number", nullable = false, unique = true, length = 32)
    private String registrationNumber;

    @Column(name = "capacity_litres", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityLitres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TankerStatus status;

    public boolean isActive() {
        return status == TankerStatus.ACTIVE;
    }
}

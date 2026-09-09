package com.zenalyst.milkcollection.farmer.entity;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.village.entity.Village;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A milk producer.
 *
 * <p>A farmer has both a village and a collection point. These are not constrained to match:
 * the village is where the farmer lives, and their assigned collection point may sit in a
 * neighbouring village. Only the collection point determines where their milk is picked up.
 *
 * <p>Expected quantities are per shift and drive planning (capacity and feasibility checks).
 * They are expectations, not measurements - actual litres live in {@code milk_collection}.
 */
@Entity
@Table(name = "farmer")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Farmer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_code", nullable = false, unique = true, length = 32)
    private String farmerCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "village_id", nullable = false)
    private Village village;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_point_id", nullable = false)
    private CollectionPoint collectionPoint;

    @Column(name = "expected_morning_quantity_litres", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedMorningQuantityLitres;

    @Column(name = "expected_evening_quantity_litres", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedEveningQuantityLitres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntityStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public BigDecimal expectedQuantityFor(Shift shift) {
        return shift == Shift.MORNING ? expectedMorningQuantityLitres : expectedEveningQuantityLitres;
    }

    public boolean isActive() {
        return status == EntityStatus.ACTIVE;
    }
}

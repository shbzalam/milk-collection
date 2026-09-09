package com.zenalyst.milkcollection.tracking.entity;

import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/**
 * One reported position of a tanker. Rows are append-only: the history is what lets anyone
 * reconstruct where a tanker was when a load went wrong.
 *
 * <p>{@code collectionRun} is optional, because a tanker reports its position whether or not it
 * is currently working a run.
 */
@Entity
@Table(name = "tanker_location")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TankerLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tanker_id", nullable = false)
    private Tanker tanker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_run_id")
    private CollectionRun collectionRun;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public Coordinates coordinates() {
        return new Coordinates(latitude, longitude);
    }
}

package com.zenalyst.milkcollection.common.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinatesTest {

    @Test
    @DisplayName("haversine distance matches a known reference pair")
    void computesKnownDistance() {
        // Pune (18.5204, 73.8567) to Mumbai (19.0760, 72.8777): ~120 km great-circle.
        Coordinates pune = new Coordinates(18.5204, 73.8567);
        Coordinates mumbai = new Coordinates(19.0760, 72.8777);

        assertThat(pune.haversineDistanceKm(mumbai)).isCloseTo(119.9, org.assertj.core.data.Offset.offset(1.5));
    }

    @Test
    void distanceIsZeroForTheSamePoint() {
        Coordinates point = new Coordinates(18.5, 73.8);
        assertThat(point.haversineDistanceKm(point)).isZero();
    }

    @Test
    void distanceIsSymmetric() {
        Coordinates a = new Coordinates(18.50, 73.80);
        Coordinates b = new Coordinates(18.62, 73.95);

        assertThat(a.haversineDistanceKm(b)).isEqualTo(b.haversineDistanceKm(a));
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> new Coordinates(91.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinates(0.0, 181.0)).isInstanceOf(IllegalArgumentException.class);
    }
}

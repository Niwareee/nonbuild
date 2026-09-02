package fr.niware.nonbuild.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointTest {

    @Test
    void ofSansRotation() {
        Point point = Point.of(1.5, 2.5, 3.5);
        assertEquals(1.5, point.x());
        assertEquals(2.5, point.y());
        assertEquals(3.5, point.z());
        assertEquals(0f, point.yaw());
        assertEquals(0f, point.pitch());
    }

    @Test
    void withOffsetDeplaceEnConserverLaRotation() {
        Point point = new Point(1, 2, 3, 90.5f, -12.25f);
        Point moved = point.withOffset(10, -2, 0.5);
        assertEquals(11, moved.x());
        assertEquals(0, moved.y());
        assertEquals(3.5, moved.z());
        assertEquals(90.5f, moved.yaw());
        assertEquals(-12.25f, moved.pitch());
    }

    @Test
    void withOffsetZeroEstIdentique() {
        Point point = new Point(4.25, 61, -7.75, 180f, 0f);
        assertEquals(point, point.withOffset(0, 0, 0));
    }
}

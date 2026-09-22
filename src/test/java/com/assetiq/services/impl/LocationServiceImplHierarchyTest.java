package com.assetiq.services.impl;

import com.assetiq.models.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationServiceImplHierarchyTest {

    private static Location loc(Location parent) {
        Location l = new Location();
        l.setId(UUID.randomUUID());
        l.setParentLocation(parent);
        return l;
    }

    @Test
    void refusesMovingALocationUnderItsOwnDescendant() {
        Location a = loc(null);
        Location b = loc(a);
        Location c = loc(b);
        assertThatThrownBy(() -> LocationServiceImpl.assertNotDescendant(c, a.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        Location other = loc(null);
        assertThatCode(() -> LocationServiceImpl.assertNotDescendant(other, a.getId())).doesNotThrowAnyException();
    }
}

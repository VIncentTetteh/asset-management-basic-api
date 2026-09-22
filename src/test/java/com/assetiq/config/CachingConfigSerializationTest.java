package com.assetiq.config;

import com.assetiq.dto.CategoryDto;
import com.assetiq.dto.DepartmentDto;
import com.assetiq.dto.DepreciationPolicyDto;
import com.assetiq.dto.LocationDto;
import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.dto.RoleDto;
import com.assetiq.dto.SubscriptionPlanDto;
import com.assetiq.dto.SupplierDto;
import com.assetiq.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every value a {@code @Cacheable} method returns must survive the Redis value
 * serializer. The generic serializer's default ObjectMapper had no JSR-310
 * module, so any cached DTO with an Instant failed to write and 500'd the request.
 */
@DisplayName("Redis cache values round-trip through the configured serializer")
class CachingConfigSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer = CachingConfig.redisValueSerializer();

    /** The DTO types returned (alone or in a Set/List) by @Cacheable methods. */
    @ParameterizedTest
    @ValueSource(classes = {SupplierDto.class, LocationDto.class, RoleDto.class, CategoryDto.class,
            DepreciationPolicyDto.class, UserDto.class, DepartmentDto.class, SubscriptionPlanDto.class,
            OrgSsoConfigDto.class})
    void cachedDtoRoundTripsAloneAndInCollections(Class<?> type) throws Exception {
        Object dto = populated(type);

        assertThat(roundTrip(dto)).isEqualTo(dto);

        Set<Object> set = new HashSet<>(Set.of(dto));
        Object setBack = roundTrip(set);
        assertThat(setBack).isInstanceOf(Set.class).isEqualTo(set);

        List<Object> list = new ArrayList<>(List.of(dto));
        Object listBack = roundTrip(list);
        assertThat(listBack).isInstanceOf(List.class).isEqualTo(list);
    }

    @Test
    void supplierCreatedAtIsWrittenAsIsoText() {
        SupplierDto dto = new SupplierDto();
        dto.setId(UUID.randomUUID());
        dto.setCreatedAt(Instant.parse("2026-09-21T10:15:30Z"));
        String json = new String(serializer.serialize(new HashSet<>(Set.of(dto))));
        assertThat(json).contains("2026-09-21T10:15:30Z");
    }

    @Test
    void permissionListsRoundTrip() {
        List<String> perms = new ArrayList<>(List.of("VIEW_ASSETS", "MANAGE_ROLES"));
        assertThat(roundTrip(perms)).isEqualTo(perms);
        List<UUID> roleIds = new ArrayList<>(List.of(UUID.randomUUID()));
        assertThat(roundTrip(roleIds)).isEqualTo(roleIds);
    }

    @Test
    void errorHandlerSwallowsSerializerFailures() {
        var handler = new CachingConfig().errorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("suppliers");
        SerializationException boom = new SerializationException("Could not write JSON");

        assertThatCode(() -> {
            handler.handleCachePutError(boom, cache, "k", new Object());
            handler.handleCacheGetError(boom, cache, "k");
            handler.handleCacheEvictError(boom, cache, "k");
            handler.handleCacheClearError(boom, cache);
        }).doesNotThrowAnyException();
    }

    private Object roundTrip(Object value) {
        return serializer.deserialize(serializer.serialize(value));
    }

    /** Instantiates the DTO and fills every simple field so dates are exercised. */
    private static Object populated(Class<?> type) throws Exception {
        Object dto = type.getDeclaredConstructor().newInstance();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                // Write-only fields (e.g. UserDto.password) are never serialized, so
                // never cached; the services do not set them on returned DTOs.
                com.fasterxml.jackson.annotation.JsonProperty jp =
                        f.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
                if (jp != null && jp.access() == com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) continue;
                Object v = sample(f.getType());
                if (v == null) continue;
                f.setAccessible(true);
                f.set(dto, v);
            }
        }
        return dto;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object sample(Class<?> t) {
        if (t == String.class) return "x";
        if (t == UUID.class) return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (t == Instant.class) return Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (t == LocalDate.class) return LocalDate.of(2026, 9, 21);
        if (t == LocalDateTime.class) return LocalDateTime.of(2026, 9, 21, 10, 0);
        if (t == BigDecimal.class) return new BigDecimal("12.34");
        if (t == Integer.class || t == int.class) return 7;
        if (t == Long.class || t == long.class) return 7L;
        if (t == Boolean.class || t == boolean.class) return true;
        if (t == Double.class || t == double.class) return 1.5d;
        if (t.isEnum()) return ((Class<Enum>) t).getEnumConstants()[0];
        return null;
    }
}

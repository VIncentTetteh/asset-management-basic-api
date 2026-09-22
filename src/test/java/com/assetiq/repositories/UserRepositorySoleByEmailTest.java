package com.assetiq.repositories;

import com.assetiq.models.User;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link UserRepository#findSoleByEmail} never throws when an email is in several tenants. */
class UserRepositorySoleByEmailTest {

    private final UserRepository repo = mock(UserRepository.class, Mockito.CALLS_REAL_METHODS);

    @Test
    void oneMatch_isReturned() {
        User user = new User();
        when(repo.findAllByEmail("a@x.com")).thenReturn(List.of(user));
        assertThat(repo.findSoleByEmail("a@x.com")).containsSame(user);
    }

    @Test
    void noneOrSeveral_isEmpty_notAnException() {
        when(repo.findAllByEmail("none@x.com")).thenReturn(List.of());
        when(repo.findAllByEmail("two@x.com")).thenReturn(List.of(new User(), new User()));
        assertThat(repo.findSoleByEmail("none@x.com")).isEmpty();
        assertThat(repo.findSoleByEmail("two@x.com")).isEmpty();
    }
}

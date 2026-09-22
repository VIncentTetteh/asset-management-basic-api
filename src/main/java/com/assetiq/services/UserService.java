package com.assetiq.services;

import com.assetiq.dto.UserDto;
import java.util.Set;
import java.util.UUID;

public interface UserService {
    UserDto createUser(UserDto dto);

    UserDto getUserById(UUID id);

    /** Return the currently-authenticated user's own profile (no admin required). */
    UserDto getMe(String email);

    Set<UserDto> listUsers();

    Set<UserDto> listUsersByDepartment(UUID departmentId);

    UserDto updateUser(UUID id, UserDto dto);
    UserDto patchUser(UUID id, UserDto dto);

    /**
     * Self-service profile update — any authenticated user may call this to change
     * their own firstName, lastName, phone, and jobTitle.  Privileged fields such
     * as role, department, status, and organisationId are ignored.
     */
    UserDto patchMe(String email, UserDto dto);

    /**
     * Changes the signed-in user's password after checking the current one, then
     * signs out every session (including this one) and records an audit event.
     */
    void changeOwnPassword(String email, com.assetiq.dto.ChangePasswordRequest request);

    UserDto deactivateUser(UUID id);

    /** Re-enables a deactivated user (fresh MFA at the controller). */
    UserDto activateUser(UUID id);

    UserDto assignRole(UUID userId, UUID roleId);
}

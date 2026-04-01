package com.example.demo.services;

import com.example.demo.dto.UserDto;
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

    UserDto deactivateUser(UUID id);

    UserDto assignRole(UUID userId, UUID roleId);
}

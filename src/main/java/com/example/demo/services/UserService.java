package com.example.demo.services;

import com.example.demo.dto.UserDto;
import java.util.Set;
import java.util.UUID;

public interface UserService {
    UserDto createUser(UserDto dto);

    UserDto getUserById(UUID id);

    Set<UserDto> listUsers();

    Set<UserDto> listUsersByDepartment(UUID departmentId);

    UserDto updateUser(UUID id, UserDto dto);

    UserDto deactivateUser(UUID id);

    UserDto assignRole(UUID userId, UUID roleId);
}

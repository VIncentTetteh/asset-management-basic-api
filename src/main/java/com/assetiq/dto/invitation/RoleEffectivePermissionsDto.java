package com.assetiq.dto.invitation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a screen needs to answer "what will this person be able to do?"
 * without inventing its own vocabulary for authority strings.
 *
 * @param roleId              the role
 * @param roleName            its name
 * @param description         its description
 * @param systemRole          true when it is built in and cannot be edited
 * @param grantAllPermissions true when it carries every permission there is
 * @param permissionCount     how many permissions are in effect
 * @param permissions         each one, described, ordered by group
 * @param byGroup             the same entries bucketed by product area
 * @param unenforced          keys that are granted but gate nothing today
 */
public record RoleEffectivePermissionsDto(UUID roleId, String roleName, String description,
                                          boolean systemRole, boolean grantAllPermissions,
                                          int permissionCount,
                                          List<PermissionDescriptionDto> permissions,
                                          Map<String, List<PermissionDescriptionDto>> byGroup,
                                          List<String> unenforced) {
}

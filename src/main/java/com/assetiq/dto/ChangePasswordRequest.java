package com.assetiq.dto;

import com.assetiq.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body of POST /users/me/password. */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Enter your current password")
    private String currentPassword;

    @NotBlank(message = "Enter a new password")
    @ValidPassword
    private String newPassword;
}

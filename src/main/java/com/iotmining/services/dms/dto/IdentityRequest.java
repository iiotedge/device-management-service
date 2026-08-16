package com.iotmining.services.dms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for mapping physical edge hardware to a System UUID.
 */
@Data
public class IdentityRequest {

    @NotBlank(message = "Hardware ID is required")
    @Size(max = 100, message = "Hardware ID must not exceed 100 characters")
    // Optional: Prevents weird hidden characters or SQL injection attempts in the hardware ID
    @Pattern(regexp = "^[a-zA-Z0-9:-]+$", message = "Hardware ID can only contain letters, numbers, colons, and hyphens")
    private String hardwareId;

    @NotBlank(message = "Identity type is required")
    @Size(max = 50, message = "Identity type must not exceed 50 characters")
    // Ensures the UI only sends approved types
    @Pattern(regexp = "^(IMEI|MAC|SN|TOKEN)$", message = "Identity type must be one of: IMEI, MAC, SN, TOKEN")
    private String identityType;
}
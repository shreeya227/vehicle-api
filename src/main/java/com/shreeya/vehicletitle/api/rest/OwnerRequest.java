package com.shreeya.vehicletitle.api.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OwnerRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String addressLine1,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state,
        @NotBlank @Pattern(regexp = "[0-9]{5}(-[0-9]{4})?") String postalCode
) {
}

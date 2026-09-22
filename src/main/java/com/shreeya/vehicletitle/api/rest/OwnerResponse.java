package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.domain.OwnerDetails;

public record OwnerResponse(
        String fullName,
        String email,
        String addressLine1,
        String city,
        String state,
        String postalCode
) {
    static OwnerResponse from(OwnerDetails owner) {
        return new OwnerResponse(owner.getFullName(), owner.getEmail(), owner.getAddressLine1(),
                owner.getCity(), owner.getState(), owner.getPostalCode());
    }
}

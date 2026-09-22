package com.shreeya.vehicletitle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OwnerDetails {

    @Column(name = "owner_full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "owner_email", nullable = false, length = 254)
    private String email;

    @Column(name = "owner_address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "owner_city", nullable = false, length = 100)
    private String city;

    @Column(name = "owner_state", nullable = false, length = 2)
    private String state;

    @Column(name = "owner_postal_code", nullable = false, length = 10)
    private String postalCode;

    protected OwnerDetails() {
    }

    public OwnerDetails(String fullName, String email, String addressLine1,
                        String city, String state, String postalCode) {
        this.fullName = fullName;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getAddressLine1() { return addressLine1; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
}

package com.duong.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Order-owned address snapshot, independent of the customer's current profile.
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {
    @Column(name = "shipping_address_line1", length = 255)
    private String addressLine1;
    @Column(name = "shipping_address_line2", length = 255)
    private String addressLine2;
    @Column(name = "shipping_ward", length = 100)
    private String ward;
    @Column(name = "shipping_city", length = 100)
    private String city;
    @Column(name = "shipping_postal_code", length = 32)
    private String postalCode;
    @Column(name = "shipping_country_code", length = 2)
    private String countryCode;
}

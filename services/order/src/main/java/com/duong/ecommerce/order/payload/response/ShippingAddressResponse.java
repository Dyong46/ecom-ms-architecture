package com.duong.ecommerce.order.payload.response;

public record ShippingAddressResponse(String addressLine1, String addressLine2,
                                      String ward, String city, String postalCode, String countryCode) {
}

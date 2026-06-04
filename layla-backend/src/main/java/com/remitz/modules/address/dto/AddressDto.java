package com.remitz.modules.address.dto;

import lombok.Data;

@Data
public class AddressDto {
    private String street;
    private String city;
    private String postcode;
    private String state;
    private String fullAddress;
    private String address2;
}

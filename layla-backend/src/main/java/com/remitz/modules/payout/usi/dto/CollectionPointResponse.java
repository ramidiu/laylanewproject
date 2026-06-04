package com.remitz.modules.payout.usi.dto;

import lombok.Data;

@Data
public class CollectionPointResponse {
    private String collectionId;
    private String name;
    private String bank;
    private String deliveryBank;
    private String address;
    private String city;
    private String state;
    private String countryId;
    private String code;
    private String telephone;
    private String email;
    private String contactPerson;
}

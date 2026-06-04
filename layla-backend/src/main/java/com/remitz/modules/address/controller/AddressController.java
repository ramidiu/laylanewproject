package com.remitz.modules.address.controller;

import com.remitz.modules.address.dto.AddressDto;
import com.remitz.modules.address.service.SmartyAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final SmartyAddressService smartyService;

    /** Autocomplete suggestions. Returns raw Smarty JSON with candidates array.
     *  Pass {@code selected} to expand a multi-unit container address. */
    @GetMapping("/lookup")
    public ResponseEntity<String> lookup(
            @RequestParam String query,
            @RequestParam(defaultValue = "GB") String country,
            @RequestParam(required = false, defaultValue = "") String selected) {
        log.debug("Address lookup: query={} country={} selected={}", query, country, selected);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(smartyService.lookup(query, country, selected));
    }

    /** Retrieve full address details for a selected address ID. */
    @GetMapping("/retrieve")
    public ResponseEntity<AddressDto> retrieve(
            @RequestParam String addressId,
            @RequestParam(defaultValue = "GB") String country) {
        log.debug("Address retrieve: addressId={} country={}", addressId, country);
        AddressDto dto = smartyService.retrieve(addressId, country);
        return ResponseEntity.ok(dto);
    }
}

package com.remitz.modules.address.service;

import com.remitz.modules.address.dto.AddressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class SmartyAddressService {

    @Value("${smarty.api-key}")
    private String apiKey;

    @Value("${smarty.referer:https://laylamoneytransfer.co.uk}")
    private String referer;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String lookup(String query, String country, String selected) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl("https://international-autocomplete.api.smarty.com/v2/lookup")
                .queryParam("search", query)
                .queryParam("country", country)
                .queryParam("key", apiKey);
        if (selected != null && !selected.isBlank()) {
            builder.queryParam("selected", selected);
        }
        String url = builder.build().toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Referer", referer);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Smarty lookup failed: {}", e.getMessage());
            return "{\"candidates\":[]}";
        }
    }

    public AddressDto retrieve(String addressId, String country) {
        String url = "https://international-autocomplete.api.smarty.com/v2/lookup/"
                + addressId
                + "?country=" + country
                + "&key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Referer", referer);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) throw new RuntimeException("Empty response from Smarty");

            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) throw new RuntimeException("No candidates in response");

            JsonNode addr = candidates.get(0);
            AddressDto dto = new AddressDto();
            dto.setStreet(text(addr, "street"));
            dto.setCity(text(addr, "locality").isEmpty() ? text(addr, "administrative_area") : text(addr, "locality"));
            dto.setPostcode(text(addr, "postal_code"));
            dto.setState(text(addr, "administrative_area"));
            dto.setAddress2(text(addr, "locality"));

            String addressText = text(addr, "address_text");
            if (!addressText.isEmpty()) {
                dto.setFullAddress(addressText);
            } else {
                StringBuilder sb = new StringBuilder();
                if (!dto.getStreet().isEmpty()) sb.append(dto.getStreet());
                if (!dto.getCity().isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(dto.getCity()); }
                if (!dto.getPostcode().isEmpty()) { if (sb.length() > 0) sb.append(" "); sb.append(dto.getPostcode()); }
                dto.setFullAddress(sb.toString().trim());
            }

            // Fallback: parse from address_text when API doesn't return individual fields
            if (dto.getPostcode().isEmpty() && !addressText.isEmpty()) {
                java.util.regex.Pattern ukPostcode = java.util.regex.Pattern
                    .compile("\\b([A-Z]{1,2}[0-9][0-9A-Z]?\\s*[0-9][A-Z]{2})\\b",
                             java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = ukPostcode.matcher(addressText);
                if (m.find()) {
                    String pc = m.group(1).trim().toUpperCase();
                    dto.setPostcode(pc);
                    String beforePc = addressText.substring(0, m.start()).trim();
                    if (!beforePc.isEmpty()) {
                        String[] tokens = beforePc.split("\\s+");
                        if (tokens.length > 0) {
                            dto.setCity(tokens[tokens.length - 1]);
                            String street = beforePc.lastIndexOf(tokens[tokens.length - 1]) > 0
                                    ? beforePc.substring(0, beforePc.lastIndexOf(tokens[tokens.length - 1])).trim()
                                    : beforePc;
                            dto.setStreet(street);
                        }
                    }
                }
            }

            log.info("Smarty retrieve: street={} city={} postcode={}", dto.getStreet(), dto.getCity(), dto.getPostcode());
            return dto;

        } catch (Exception e) {
            log.error("Smarty retrieve failed for id={}: {}", addressId, e.getMessage());
            throw new RuntimeException("Address lookup failed: " + e.getMessage());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText("") : "";
    }
}

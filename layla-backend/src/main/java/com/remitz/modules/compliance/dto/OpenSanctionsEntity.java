package com.remitz.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenSanctionsEntity {

    @JsonProperty("id")
    private String id;

    @JsonProperty("caption")
    private String caption;

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("properties")
    private Map<String, List<Object>> properties;

    @JsonProperty("datasets")
    private List<String> datasets;

    @JsonProperty("referents")
    private List<String> referents;
}

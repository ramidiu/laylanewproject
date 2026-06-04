package com.remitz.modules.compliance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.compliance.opensanctions")
@Getter
@Setter
public class OpenSanctionsProperties {

    private boolean enabled = true;

    private String baseUrl = "https://data.opensanctions.org/datasets/latest";

    private String downloadDir = System.getProperty("java.io.tmpdir");

    private int batchSize = 500;

    private int maxEntries = 0;

    private List<Dataset> datasets = defaultDatasets();

    private static List<Dataset> defaultDatasets() {
        List<Dataset> list = new ArrayList<>();
        list.add(new Dataset("sanctions", "SANCTIONS"));
        list.add(new Dataset("peps", "PEP"));
        return list;
    }

    @Getter
    @Setter
    public static class Dataset {
        private String id;
        private String defaultListType;

        public Dataset() {}

        public Dataset(String id, String defaultListType) {
            this.id = id;
            this.defaultListType = defaultListType;
        }
    }
}

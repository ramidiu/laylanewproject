package com.remitz.modules.compliance.entity;

import com.remitz.common.enums.ScreeningListType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sanctions_lists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionsListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "source_code", length = 64)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", length = 20)
    private ListType listType;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_name")
    private ScreeningListType listName;

    @Column(name = "entry_name", nullable = false, length = 500)
    private String entryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "schema_type", length = 40)
    private String schemaType;

    @Column(columnDefinition = "JSON")
    private String aliases;

    @Column(name = "topics", columnDefinition = "JSON")
    private String topics;

    @Column(length = 3)
    private String country;

    @Column(name = "nationalities", columnDefinition = "JSON")
    private String nationalities;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "additional_info", columnDefinition = "JSON")
    private String additionalInfo;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum EntryType {
        INDIVIDUAL, ENTITY
    }

    public enum ListType {
        SANCTIONS, PEP, CRIME, DEBARMENT, OTHER
    }
}

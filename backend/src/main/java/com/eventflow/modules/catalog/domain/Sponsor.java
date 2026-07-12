package com.eventflow.modules.catalog.domain;

import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Patrocinador (administrado por ADMIN; se vincula a eventos vía sponsor_events). */
@Entity
@Table(name = "sponsors", schema = "catalog")
public class Sponsor {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url")
    private String logoUrl;

    private String website;

    protected Sponsor() {
    }

    private Sponsor(UUID id, String name, String logoUrl, String website) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.website = website;
    }

    public static Sponsor create(String name, String logoUrl, String website) {
        requireValidName(name);
        return new Sponsor(Uuids.v7(), name, logoUrl, website);
    }

    public void update(String name, String logoUrl, String website) {
        requireValidName(name);
        this.name = name;
        this.logoUrl = logoUrl;
        this.website = website;
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name es obligatorio (máx 120)");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getWebsite() {
        return website;
    }
}

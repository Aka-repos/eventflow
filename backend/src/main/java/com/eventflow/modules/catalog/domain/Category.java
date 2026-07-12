package com.eventflow.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Categoría de evento (id SMALLINT generado por la BD; nombre único). */
@Entity
@Table(name = "categories", schema = "catalog")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(nullable = false)
    private boolean active;

    protected Category() {
    }

    private Category(String name, String icon, boolean active) {
        this.name = name;
        this.icon = icon;
        this.active = active;
    }

    public static Category create(String name, String icon, boolean active) {
        requireValidName(name);
        return new Category(name, icon, active);
    }

    public void rename(String name, String icon, boolean active) {
        requireValidName(name);
        this.name = name;
        this.icon = icon;
        this.active = active;
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("name es obligatorio (máx 80)");
        }
    }

    public Short getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isActive() {
        return active;
    }
}

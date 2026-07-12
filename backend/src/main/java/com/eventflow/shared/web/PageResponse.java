package com.eventflow.shared.web;

import java.util.List;

/** Envelope de páginas del contrato: { "data": [...], "meta": CursorMeta } (api/01 §6, api/07). */
public record PageResponse<T>(List<T> data, CursorMetaDto meta) {

    public static <T> PageResponse<T> of(List<T> data, CursorPage<?> page) {
        return new PageResponse<>(data, CursorMetaDto.from(page));
    }
}

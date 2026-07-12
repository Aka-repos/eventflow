package com.eventflow.shared.web;

/** Espejo del schema CursorMeta. */
public record CursorMetaDto(boolean hasNext, String nextCursor) {

    public static CursorMetaDto from(CursorPage<?> page) {
        return new CursorMetaDto(page.hasNext(), page.nextCursor());
    }
}

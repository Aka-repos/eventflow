package com.eventflow.shared.web;

import java.util.List;

/** Página keyset (api/07): items + cursor opaco de la siguiente página (null = fin). */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public boolean hasNext() {
        return nextCursor != null;
    }
}

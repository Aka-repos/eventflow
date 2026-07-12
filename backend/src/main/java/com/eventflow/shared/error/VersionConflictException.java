package com.eventflow.shared.error;

/** If-Match ≠ versión vigente (optimistic lock, api/01 §9). Incluye conflictVersion para recargar. */
public class VersionConflictException extends DomainException {

    private final int currentVersion;

    public VersionConflictException(int currentVersion) {
        super(ErrorCode.VERSION_CONFLICT, "La versión enviada en If-Match no es la vigente");
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }
}

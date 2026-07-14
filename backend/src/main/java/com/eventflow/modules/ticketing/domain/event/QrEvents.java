package com.eventflow.modules.ticketing.domain.event;

/** Domain events de QR/check-in (api/08 §2). Jamás incluyen el token firmado. */
public final class QrEvents {

    public static final String QR_GENERATED = "QRCodeGenerated";
    public static final String QR_INVALIDATED = "QRCodeInvalidated";
    public static final String CHECKIN_COMPLETED = "CheckInCompleted";
    public static final String CHECKIN_DENIED = "CheckInDenied";
    public static final int VERSION = 1;

    private QrEvents() {
    }
}

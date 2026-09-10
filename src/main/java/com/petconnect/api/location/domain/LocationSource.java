package com.petconnect.api.location.domain;

/** Origem de um registro de localização. */
public enum LocationSource {
    /** Tutor registrou manualmente pelo app. */
    TUTOR,
    /** Scan público do QR Code do pet. */
    PUBLIC_QR
}

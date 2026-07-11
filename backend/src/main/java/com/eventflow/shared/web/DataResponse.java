package com.eventflow.shared.web;

/** Envelope de éxito del contrato: { "data": ... } (docs/api/01 §6). */
public record DataResponse<T>(T data) {

    public static <T> DataResponse<T> of(T data) {
        return new DataResponse<>(data);
    }
}

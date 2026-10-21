package com.servicepros.common;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String error, String message, String path, Instant timestamp,
                       Map<String, String> fieldErrors) {

    /** Minimal JSON for filters that run before Spring MVC (no message converters there). */
    public static String json(int status, String error, String message, String path) {
        return "{\"status\":" + status
                + ",\"error\":\"" + escape(error)
                + "\",\"message\":\"" + escape(message)
                + "\",\"path\":\"" + escape(path)
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}

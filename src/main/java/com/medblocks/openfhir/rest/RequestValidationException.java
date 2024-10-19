package com.medblocks.openfhir.rest;

import lombok.Getter;

import java.util.List;

public class RequestValidationException extends RuntimeException {
    @Getter
    private final List<String> messages;

    public RequestValidationException(String message, List<String> messages) {
        super(message);
        this.messages = messages;
    }
}

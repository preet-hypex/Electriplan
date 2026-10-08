package com.hypex.electriplan.reference.domain;

/** The address finder is turned off, or its provider did not answer in time. Typing the address still works. */
public class AddressSearchUnavailableException extends RuntimeException {

    public AddressSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AddressSearchUnavailableException(String message) {
        super(message);
    }
}

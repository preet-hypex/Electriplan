package com.hypex.electriplan.files.domain;

/** No such file in the request's company (or its bytes are gone). */
public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException() {
        super("No such file in this company.");
    }
}

package com.hypex.electriplan.projects.dto;

/** The same {"message"} body as every other refusal (tenancy's ErrorMessage). */
@io.swagger.v3.oas.annotations.media.Schema(name = "ErrorMessage")
public record ErrorMessage(String message) {
}

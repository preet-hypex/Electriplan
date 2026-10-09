package com.hypex.electriplan.files.domain;

/** What a stored file is for: the codes of electriplan.stored_file.purpose. */
public enum FilePurpose {
    FLOOR_PLAN_SOURCE,
    FLOOR_PLAN_EXPORT,
    ELECTRICAL_DRAWING,
    QUOTE_DOCUMENT,
    LICENCE_DOCUMENT,
    ATTACHMENT;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

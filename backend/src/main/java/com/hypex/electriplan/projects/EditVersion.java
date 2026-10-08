package com.hypex.electriplan.projects;

import org.jspecify.annotations.Nullable;

/**
 * Optimistic locking, from the caller's side: a change names the version it
 * was made from. If the row has moved on since, someone else changed it, and
 * the change is refused (409) rather than silently overwriting theirs.
 */
final class EditVersion {

    private EditVersion() {
    }

    /**
     * @param sent the version the caller read (from the form)
     * @param current the row's version now
     */
    static void requireUnchanged(@Nullable Integer sent, int current) {
        if (sent == null) {
            throw ProjectsProblem.invalid("version", "Send the version you are changing (from when you opened it)");
        }
        if (sent != current) {
            throw ProjectsProblem.conflict(ProjectsErrors.CHANGED_SINCE);
        }
    }
}

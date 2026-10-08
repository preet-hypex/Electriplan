package com.hypex.electriplan.projects.dao;

import java.util.Locale;

import com.hypex.electriplan.projects.domain.ProjectStatus;
import com.hypex.electriplan.projects.entity.ProjectEntity;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * How the project list finds projects. Each filter is one small rule; the
 * list combines them with {@link #matching}.
 */
public final class ProjectSearch {

    /** Most recently active first; the newer reference breaks a tie. */
    public static final Sort NEWEST_ACTIVITY_FIRST = Sort.by(Sort.Order.desc("lastActivityAt"), Sort.Order.desc("reference"));

    private ProjectSearch() {
    }

    /** Projects that are (or are not) archived, have the status if given, and mention the words if given. */
    public static Specification<ProjectEntity> matching(@Nullable String words, @Nullable ProjectStatus status, boolean archived) {
        return (archived ? isArchived() : isNotArchived())
                .and(hasStatus(status))
                .and(mentions(words));
    }

    static Specification<ProjectEntity> isArchived() {
        return (project, query, cb) -> cb.isNotNull(project.get("archivedAt"));
    }

    static Specification<ProjectEntity> isNotArchived() {
        return (project, query, cb) -> cb.isNull(project.get("archivedAt"));
    }

    /** Any status when none is given. */
    static Specification<ProjectEntity> hasStatus(@Nullable ProjectStatus status) {
        return (project, query, cb) -> status == null ? cb.conjunction() : cb.equal(project.get("status"), status);
    }

    /** The words appear in the name, reference, street or suburb (any case). Anything when no words are given. */
    static Specification<ProjectEntity> mentions(@Nullable String words) {
        return (project, query, cb) -> {
            if (words == null || words.isBlank()) {
                return cb.conjunction();
            }
            String pattern = containsPattern(words);
            return cb.or(
                    cb.like(cb.lower(project.get("name")), pattern, '\\'),
                    cb.like(cb.lower(project.get("reference")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(project.get("siteStreet"), "")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(project.get("siteSuburb"), "")), pattern, '\\'));
        };
    }

    /** A LIKE pattern for "contains these words", where % and _ in the words are just characters. */
    static String containsPattern(String words) {
        String literal = words.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + literal + "%";
    }
}

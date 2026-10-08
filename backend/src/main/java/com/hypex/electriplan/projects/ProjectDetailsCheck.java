package com.hypex.electriplan.projects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.projects.ProjectsApi.FieldProblem;
import com.hypex.electriplan.reference.Distributor;
import com.hypex.electriplan.reference.DistributorDirectory;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Checks a project form for what its annotations cannot: the distributor
 * must be one we know and must supply the site's state, and supply is
 * single- or three-phase. (Required fields and lengths are checked before
 * this, by Bean Validation on {@link ProjectsApi.ProjectForm}.)
 */
@Component
@RequiredArgsConstructor
class ProjectDetailsCheck {

    private final DistributorDirectory distributors;

    /**
     * The form's details, ready to apply.
     *
     * @param currentPhases the project's supply now, kept when the form leaves it out
     * @throws ProjectsProblem 400 naming every field that is wrong
     */
    ProjectDetails check(ProjectsApi.ProjectForm form, int currentPhases) {
        ProjectsApi.SiteForm site = Objects.requireNonNull(form.site(), "validated: site");
        AustralianState state = Objects.requireNonNull(site.state(), "validated: site.state");
        int phases = form.supplyPhases() == null ? currentPhases : form.supplyPhases();

        String distributor = clean(form.distributor());

        List<FieldProblem> problems = new ArrayList<>();
        distributorProblem(distributor, state).ifPresent(problems::add);
        if (phases != 1 && phases != 3) {
            problems.add(new FieldProblem("supplyPhases", "Supply is single-phase (1) or three-phase (3)"));
        }
        if (!problems.isEmpty()) {
            throw ProjectsProblem.invalid(problems);
        }

        return new ProjectDetails(
                Objects.requireNonNull(form.name(), "validated: name").strip(),
                clean(form.description()),
                clean(form.lotNumber()),
                clean(site.street()),
                clean(site.suburb()),
                state,
                clean(site.postcode()),
                distributor,
                phases,
                form.dueOn(),
                form.status());
    }

    /** What is wrong with the distributor for this state, if anything. No distributor is fine. */
    private Optional<FieldProblem> distributorProblem(@Nullable String code, AustralianState state) {
        if (code == null) {
            return Optional.empty();
        }
        Optional<Distributor> found = find(code);
        if (found.isEmpty()) {
            return Optional.of(new FieldProblem("distributor",
                    "No distributor has the code '" + code + "'. " + choicesIn(state)));
        }
        if (found.get().state() != state) {
            return Optional.of(new FieldProblem("distributor",
                    found.get().name() + " supplies " + found.get().state() + ", not " + state + ". " + choicesIn(state)));
        }
        return Optional.empty();
    }

    private Optional<Distributor> find(String code) {
        try {
            return distributors.find(DistributorCode.of(code));
        } catch (IllegalArgumentException notACode) {
            return Optional.empty();
        }
    }

    /** "Choose one of citipower, jemena, ..." for the state, or what to do when it has none. */
    private String choicesIn(AustralianState state) {
        String codes = distributors.inState(state).stream().map(d -> d.code().value()).collect(Collectors.joining(", "));
        return codes.isEmpty()
                ? "No distributor in " + state + " is set up yet: leave it empty."
                : "Choose one of " + codes + ".";
    }

    /** Trimmed text, or null when there is none. */
    static @Nullable String clean(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

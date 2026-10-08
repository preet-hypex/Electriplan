package com.hypex.electriplan.model.plan;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A floor plan the engine cannot read: not JSON, not valid against
 * {@code contracts/floor-plan.schema.json}, or breaking one of the model's
 * rules. {@link #problems()} says what is wrong, one line each.
 */
public class InvalidFloorPlanException extends RuntimeException {

    private final List<String> problems;

    public InvalidFloorPlanException(List<String> problems) {
        this(problems, null);
    }

    public InvalidFloorPlanException(List<String> problems, @Nullable Throwable cause) {
        super("Not a valid floor plan: " + String.join("; ", problems), cause);
        this.problems = List.copyOf(problems);
    }

    /** What is wrong with the plan, one line each, e.g. {@code $.walls[0].thickness: must be greater than 0}. */
    public List<String> problems() {
        return problems;
    }
}

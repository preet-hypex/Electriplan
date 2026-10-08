package com.hypex.electriplan.model.design;

import java.util.List;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;

/** Something the engine could not decide and assumed instead, for the electrician to confirm. */
@Builder(toBuilder = true)
@With
public record DecisionRequired(String id, @Singular List<String> itemIds, String question) {

    public DecisionRequired {
        Checks.id(id, "id");
        itemIds = Checks.ids(itemIds, "itemIds");
        Checks.text(question, "question");
    }
}

package com.hypex.electriplan.reference.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.reference.dao.DistributorRepository;
import com.hypex.electriplan.reference.dto.Distributor;
import com.hypex.electriplan.reference.dto.ReferenceProblem;
import com.hypex.electriplan.reference.entity.DistributorEntity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The electricity distributors the application knows, and the check that a
 * project brief names one of them, in the brief's state.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DistributorDirectory {

    private final DistributorRepository distributors;

    /** Every distributor, by state then name. */
    public List<Distributor> all() {
        return distributors.findAllByOrderByStateAscNameAsc().stream().map(DistributorEntity::toDistributor).toList();
    }

    /** The distributors in one state, by name: what a brief for that state may choose from. */
    public List<Distributor> inState(AustralianState state) {
        return distributors.findByStateOrderByNameAsc(state).stream().map(DistributorEntity::toDistributor).toList();
    }

    public Optional<Distributor> find(DistributorCode code) {
        return distributors.findById(code.value()).map(DistributorEntity::toDistributor);
    }

    /**
     * What is wrong with a brief's distributor, if anything: it must be one the
     * application knows, and it must supply the brief's state. Empty means fine.
     */
    public List<ReferenceProblem> check(ProjectBrief brief) {
        List<ReferenceProblem> problems = new ArrayList<>();
        Optional<Distributor> distributor = find(brief.distributor());
        if (distributor.isEmpty()) {
            problems.add(new ReferenceProblem("distributor",
                    "No distributor has the code '" + brief.distributor() + "'. Choose one of " + codesIn(brief.state()) + "."));
        } else if (distributor.get().state() != brief.state()) {
            problems.add(new ReferenceProblem("distributor",
                    distributor.get().name() + " supplies " + distributor.get().state() + ", not " + brief.state()
                            + ". Choose one of " + codesIn(brief.state()) + "."));
        }
        return problems;
    }

    private String codesIn(AustralianState state) {
        List<String> codes = inState(state).stream().map(d -> d.code().value()).toList();
        return codes.isEmpty() ? "none yet: no distributor in " + state + " is set up" : String.join(", ", codes);
    }
}

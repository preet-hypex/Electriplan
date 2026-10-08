package com.hypex.electriplan.design;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import com.hypex.electriplan.model.Contracts;
import com.hypex.electriplan.model.Contracts.Document;
import com.hypex.electriplan.model.ModelJson;
import com.hypex.electriplan.model.Samples;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.model.design.DecisionRequired;
import com.hypex.electriplan.model.design.ElectricalDesign;
import com.hypex.electriplan.model.design.RulePackRef;
import org.junit.jupiter.api.Test;

class DesignOrchestratorTest {

    static final RulePackRef VIC_RULES = RulePackRef.builder()
            .id("au-residential").version("2026.1").state(AustralianState.VIC).standard("AS/NZS 3000:2018+A3").build();

    static DesignInput input() {
        return new DesignInput(Samples.floorPlan(), 3, Samples.brief(), List.of(Samples.shower(), Samples.cooktop()), VIC_RULES);
    }

    /** A stage that adds one decision, so its effect and its order are visible. */
    record AddDecision(String id) implements DesignStage {
        @Override
        public ElectricalDesign apply(DesignContext context) {
            return context.design().toBuilder()
                    .decisionRequired(DecisionRequired.builder().id(id).question("Asked by " + id).build())
                    .build();
        }
    }

    @Test
    void withNoStagesTheDesignIsEmptyAndValidAgainstTheSchema() {
        ElectricalDesign design = new DesignOrchestrator(List.of()).design(input());

        assertThat(design).isEqualTo(ElectricalDesign.empty(
                PlanFingerprint.planRef(Samples.floorPlan(), 3), VIC_RULES));
        assertThat(design.points()).isEmpty();
        assertThat(design.circuits()).isEmpty();
        assertThat(design.switchboard()).isNull();

        String json = ModelJson.write(design);
        assertThat(Contracts.validate(Document.ELECTRICAL_DESIGN, json)).isEmpty();
        assertThat(ModelJson.read(json, ElectricalDesign.class)).isEqualTo(design);
    }

    @Test
    void theDesignRecordsThePlanItWasMadeFromAndTheRulePack() {
        ElectricalDesign design = new DesignOrchestrator(List.of()).design(input());

        assertThat(design.planRef().planHash()).isEqualTo(PlanFingerprint.hash(Samples.floorPlan()));
        assertThat(design.planRef().planVersion()).isEqualTo(3);
        assertThat(design.rulePack()).isEqualTo(VIC_RULES);
    }

    @Test
    void stagesRunInOrderEachSeeingTheDesignTheOneBeforeReturned() {
        List<List<String>> seen = new ArrayList<>();
        DesignStage recordFirst = context -> {
            seen.add(decisionIds(context.design()));
            return new AddDecision("d_first").apply(context);
        };
        DesignStage recordSecond = context -> {
            seen.add(decisionIds(context.design()));
            assertThat(context.plan()).isEqualTo(Samples.floorPlan());
            assertThat(context.brief()).isEqualTo(Samples.brief());
            assertThat(context.fixtures()).containsExactly(Samples.shower(), Samples.cooktop());
            assertThat(context.rulePack()).isEqualTo(VIC_RULES);
            return new AddDecision("d_second").apply(context);
        };
        DesignStage recordThird = context -> {
            seen.add(decisionIds(context.design()));
            return context.design();
        };

        ElectricalDesign design = new DesignOrchestrator(List.of(recordFirst, recordSecond, recordThird)).design(input());

        assertThat(seen).containsExactly(List.of(), List.of("d_first"), List.of("d_first", "d_second"));
        assertThat(decisionIds(design)).containsExactly("d_first", "d_second");
        assertThat(Contracts.validate(Document.ELECTRICAL_DESIGN, ModelJson.write(design))).isEmpty();
    }

    @Test
    void theSameInputGivesTheSameDesignByteForByte() {
        DesignOrchestrator orchestrator = new DesignOrchestrator(List.of(new AddDecision("d_001"), new AddDecision("d_002")));

        String first = ModelJson.write(orchestrator.design(input()));
        String second = ModelJson.write(orchestrator.design(input()));
        String fromAFreshOrchestrator = ModelJson.write(
                new DesignOrchestrator(List.of(new AddDecision("d_001"), new AddDecision("d_002"))).design(input()));

        assertThat(second).isEqualTo(first);
        assertThat(fromAFreshOrchestrator).isEqualTo(first);
    }

    @Test
    void aStageMayNotChangeWhichPlanOrRulePackTheDesignIsFor() {
        DesignStage otherRules = context -> context.design().withRulePack(VIC_RULES.withVersion("2027.1"));
        DesignStage otherPlan = context -> context.design().withPlanRef(context.design().planRef().withPlanVersion(4));

        assertThatThrownBy(() -> new DesignOrchestrator(List.of(otherRules)).design(input()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed which plan or rule pack");
        assertThatThrownBy(() -> new DesignOrchestrator(List.of(otherPlan)).design(input()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theRulePackMustBeForTheBriefsState() {
        assertThatThrownBy(() -> new DesignInput(Samples.floorPlan(), 1, Samples.brief(), List.of(),
                VIC_RULES.withState(AustralianState.QLD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QLD");
    }

    @Test
    void thePlanRevisionStartsAtOne() {
        assertThatThrownBy(() -> new DesignInput(Samples.floorPlan(), 0, Samples.brief(), List.of(), VIC_RULES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStageListIsFixedWhenTheOrchestratorIsMade() {
        List<DesignStage> stages = new ArrayList<>(List.of(new AddDecision("d_001")));
        DesignOrchestrator orchestrator = new DesignOrchestrator(stages);
        stages.add(new AddDecision("d_002"));

        assertThat(orchestrator.stages()).hasSize(1);
        assertThat(decisionIds(orchestrator.design(input()))).containsExactly("d_001");
    }

    private static List<String> decisionIds(ElectricalDesign design) {
        return design.decisionsRequired().stream().map(DecisionRequired::id).toList();
    }
}

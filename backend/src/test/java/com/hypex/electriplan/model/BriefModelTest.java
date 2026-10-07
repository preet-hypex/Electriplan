package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypex.electriplan.model.brief.Construction;
import com.hypex.electriplan.model.brief.Preferences;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.brief.Supply;
import com.hypex.electriplan.model.units.Metres;
import com.hypex.electriplan.model.units.Millimetres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BriefModelTest {

    @Test
    void theBuilderSetsTheCurrentVersion() {
        assertThat(Samples.brief().version()).isEqualTo(ProjectBrief.VERSION);
    }

    @Test
    void aBriefNeedsEveryRequiredPart() {
        ProjectBrief brief = Samples.brief();
        assertThatThrownBy(() -> brief.toBuilder().supply(null).build()).hasMessage("supply is required");
        assertThatThrownBy(() -> brief.toBuilder().appliances(null).build()).hasMessage("appliances is required");
        assertThatThrownBy(() -> brief.withState(null)).hasMessage("state is required");
        assertThatThrownBy(() -> brief.withDistributor(null)).hasMessage("distributor is required");
        assertThatThrownBy(() -> brief.withVersion(2)).hasMessageContaining("version must be one of [1]");
    }

    @Test
    void preferencesAreOptional() {
        assertThat(Samples.brief().withPreferences(null).preferences()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 4})
    void supplyIsSingleOrThreePhase(int phases) {
        assertThatThrownBy(() -> new Supply(phases, 230, null)).hasMessageContaining("phases must be one of [1, 3]");
    }

    @Test
    void supplyIsTwoHundredAndThirtyVolts() {
        assertThat(new Supply(3, 230, null).phases()).isEqualTo(3);
        assertThatThrownBy(() -> new Supply(1, 240, null)).hasMessageContaining("nominalVoltage");
        assertThatThrownBy(() -> new Supply(1, 230, Metres.of(0))).hasMessageContaining("consumerMainsLengthM must be greater than zero");
    }

    @Test
    void constructionLimits() {
        Construction ok = new Construction(1, Millimetres.of(2550), true, true, true);
        assertThatThrownBy(() -> ok.withStoreys(0)).hasMessageContaining("storeys");
        assertThatThrownBy(() -> ok.withStoreys(5)).hasMessageContaining("storeys");
        assertThatThrownBy(() -> ok.withDefaultCeilingHeight(Millimetres.of(2000))).hasMessageContaining("defaultCeilingHeight");
        assertThatThrownBy(() -> ok.withDefaultCeilingHeight(Millimetres.of(6001))).hasMessageContaining("defaultCeilingHeight");
        assertThatThrownBy(() -> ok.withSlab(null)).hasMessage("slab is required");
    }

    @Test
    void preferenceLimits() {
        assertThatThrownBy(() -> Preferences.builder().switchHeight(Millimetres.of(899)).build()).hasMessageContaining("switchHeight");
        assertThatThrownBy(() -> Preferences.builder().gpoHeight(Millimetres.of(1500)).build()).hasMessageContaining("gpoHeight");
        assertThatThrownBy(() -> Preferences.builder().downlightItemCode("dl-lower").build()).hasMessageContaining("downlightItemCode");
        assertThat(Preferences.builder().build().switchHeight()).isNull();
    }

    @Test
    void withReturnsACopyAndLeavesTheOriginal() {
        ProjectBrief brief = Samples.brief();
        ProjectBrief changed = brief.withPreferences(null);
        assertThat(brief.preferences()).isNotNull();
        assertThat(changed).isNotSameAs(brief).isNotEqualTo(brief);
        assertThat(brief.toBuilder().build()).isEqualTo(brief);
    }
}

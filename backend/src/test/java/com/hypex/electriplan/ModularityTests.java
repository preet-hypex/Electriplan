package com.hypex.electriplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    /** The electrical engine's modules (documents/electrical-engine-plan.md §6.3). */
    static final List<String> ENGINE_MODULES = List.of(
            "model", "plan", "rules", "catalogue", "geometry", "zones", "layout", "loads",
            "circuits", "cabling", "switchboard", "validation", "design", "review", "output");

    ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void theEngineModulesExist() {
        List<String> names = modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString).toList();

        assertThat(names).containsAll(ENGINE_MODULES);
        assertThat(names).doesNotContain("quoting");
    }

    @Test
    void onlyTheModelAndGeometryAreOpen() {
        List<String> open = modules.stream().filter(ApplicationModule::isOpen)
                .map(ApplicationModule::getIdentifier).map(Object::toString).toList();

        assertThat(open).containsExactlyInAnyOrder("model", "geometry");
    }
}

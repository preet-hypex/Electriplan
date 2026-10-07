package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypex.electriplan.Application;
import com.hypex.electriplan.model.Contracts.Document;
import com.hypex.electriplan.model.design.ElectricalDesign;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The model inside the running application: it is an open module that depends
 * on no other, and the application's own HTTP mapper (Spring's, which the API
 * will answer with) writes it in the shape the contracts require.
 */
@SpringBootTest
class ModelModuleIntegrationTests {

    @Autowired
    ObjectMapper springMapper;

    @Test
    void theModelIsAnOpenModuleWithNoDependencies() {
        ApplicationModules modules = ApplicationModules.of(Application.class);
        ApplicationModule model = modules.getModuleByName("model").orElseThrow();

        assertThat(model.isOpen()).isTrue();
        assertThat(model.getDisplayName()).isEqualTo("Electrical model");
        assertThat(model.getBootstrapDependencies(modules)).isEmpty();
        assertThat(model.getDirectDependencies(modules).uniqueModules()).isEmpty();
    }

    @Test
    void springsMapperWritesSchemaValidDocuments() throws Exception {
        String design = springMapper.writeValueAsString(Samples.fullDesign());
        assertThat(Contracts.validate(Document.ELECTRICAL_DESIGN, design)).isEmpty();

        String empty = springMapper.writeValueAsString(
                ElectricalDesign.empty(Samples.fullDesign().planRef(), Samples.fullDesign().rulePack()));
        assertThat(Contracts.validate(Document.ELECTRICAL_DESIGN, empty)).isEmpty();

        assertThat(Contracts.validate(Document.PROJECT_BRIEF, springMapper.writeValueAsString(Samples.brief()))).isEmpty();
        assertThat(Contracts.validate(Document.FIXTURE, springMapper.writeValueAsString(Samples.shower()))).isEmpty();
    }

    @Test
    void springsMapperReadsWhatTheModelWrites() throws Exception {
        String json = ModelJson.write(Samples.fullDesign());
        assertThat(springMapper.readValue(json, ElectricalDesign.class)).isEqualTo(Samples.fullDesign());
    }

    @Test
    void everyValidExampleSurvivesTheApplicationsMapper() throws Exception {
        for (Contracts.Example example : Contracts.examples(true)) {
            Object model = springMapper.readValue(example.json(), example.document().type);
            assertThat(Contracts.validate(example.document(), springMapper.writeValueAsString(model)))
                    .as(example.toString())
                    .isEmpty();
        }
    }
}

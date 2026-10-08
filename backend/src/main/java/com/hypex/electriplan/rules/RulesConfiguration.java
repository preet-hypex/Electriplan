package com.hypex.electriplan.rules;

import com.hypex.electriplan.model.common.AustralianState;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the rule pack once, at startup: a pack with any problem stops the API
 * from starting, with every problem in the log.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
class RulesConfiguration {

    @Bean
    RuleBook ruleBook(@Value("${electriplan.rules.pack:au-residential}") String pack,
                      @Value("${electriplan.rules.allow-unsigned-states:false}") boolean allowUnsignedStates) {
        RulePack loaded = RulePackLoader.load(RulePackSource.classpath(pack));
        RuleBook book = new RuleBook(loaded, allowUnsignedStates);
        loaded.notices().forEach(log::warn);
        log.info("Rule pack {} {} ({}): {} national rules; state files {}; signed off {}; designing {}{}",
                loaded.id(), loaded.version(), loaded.status().name().toLowerCase(java.util.Locale.ROOT),
                loaded.nationalRules().size(), loaded.states(), loaded.supportedStates(), book.designableStates(),
                allowUnsignedStates ? " (unsigned states allowed: not for production)" : "");
        if (allowUnsignedStates) {
            for (AustralianState state : loaded.states()) {
                if (!loaded.supportedStates().contains(state)) {
                    log.warn("Designing {} with rules no licensed electrician has signed off", state);
                }
            }
        }
        return book;
    }
}

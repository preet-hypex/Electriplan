package com.hypex.electriplan.projects.config;

import com.hypex.electriplan.projects.domain.ProjectStatus;
import com.hypex.electriplan.projects.entity.Codes;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Query parameters use the same lowercase codes as JSON: {@code ?status=on_hold}. */
@Configuration(proxyBeanMethods = false)
public class ProjectsWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ProjectStatus.class, code -> {
            try {
                return Codes.fromCode(ProjectStatus.class, code);
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        });
    }
}

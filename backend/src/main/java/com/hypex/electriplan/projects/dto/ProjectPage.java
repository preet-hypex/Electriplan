package com.hypex.electriplan.projects.dto;

import java.util.List;

public record ProjectPage(List<ProjectSummary> items, int page, int size, long total) {
}

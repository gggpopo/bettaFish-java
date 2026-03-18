package com.bettafish.common.api;

import java.util.List;

public record ReportDocument(
    String title,
    String summary,
    List<ReportSection> sections,
    String html
) {
}

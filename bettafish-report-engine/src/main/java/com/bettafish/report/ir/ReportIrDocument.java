package com.bettafish.report.ir;

import java.util.List;

public record ReportIrDocument(
    String title,
    String summary,
    List<String> sections
) {
}

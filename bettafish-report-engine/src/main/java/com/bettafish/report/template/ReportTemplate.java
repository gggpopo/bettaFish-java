package com.bettafish.report.template;

public record ReportTemplate(String title) {

    public static final ReportTemplate DEFAULT = new ReportTemplate("BettaFish analysis report");
}

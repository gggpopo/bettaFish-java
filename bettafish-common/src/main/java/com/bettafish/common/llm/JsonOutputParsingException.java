package com.bettafish.common.llm;

import java.util.List;

public class JsonOutputParsingException extends IllegalStateException {

    private final String rawText;
    private final String cleanedText;
    private final String extractedJson;
    private final String repairedJson;
    private final List<String> repairActions;
    private final String errorDetail;

    public JsonOutputParsingException(String message,
                                      String rawText,
                                      String cleanedText,
                                      String extractedJson,
                                      String repairedJson,
                                      List<String> repairActions,
                                      String errorDetail) {
        super(message);
        this.rawText = rawText;
        this.cleanedText = cleanedText;
        this.extractedJson = extractedJson;
        this.repairedJson = repairedJson;
        this.repairActions = List.copyOf(repairActions);
        this.errorDetail = errorDetail;
    }

    public String rawText() {
        return rawText;
    }

    public String cleanedText() {
        return cleanedText;
    }

    public String extractedJson() {
        return extractedJson;
    }

    public String repairedJson() {
        return repairedJson;
    }

    public List<String> repairActions() {
        return repairActions;
    }

    public String errorDetail() {
        return errorDetail;
    }
}

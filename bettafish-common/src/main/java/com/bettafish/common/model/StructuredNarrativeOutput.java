package com.bettafish.common.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StructuredNarrativeOutput(
    @JsonProperty("summary") String summary,
    @JsonProperty("key_points") List<String> keyPoints,
    @JsonProperty("evidence_gaps") List<String> evidenceGaps,
    @JsonProperty("final_conclusion") String finalConclusion
) {

    public StructuredNarrativeOutput {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        evidenceGaps = evidenceGaps == null ? List.of() : List.copyOf(evidenceGaps);
    }
}

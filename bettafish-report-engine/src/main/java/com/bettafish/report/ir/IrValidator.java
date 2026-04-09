package com.bettafish.report.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;

public class IrValidator {

    private static final Set<String> VALID_CHART_TYPES = Set.of(
        "bar", "line", "pie", "doughnut", "radar", "polarArea", "scatter", "bubble"
    );

    public List<String> validate(DocumentIr documentIr) {
        List<String> errors = new ArrayList<>();
        for (DocumentBlock block : documentIr.blocks()) {
            validateBlock(block, errors);
        }
        return errors;
    }

    private void validateBlock(DocumentBlock block, List<String> errors) {
        switch (block) {
            case DocumentBlock.HeadingBlock h -> {
                if (h.level() < 1 || h.level() > 6) {
                    errors.add("HeadingBlock level must be 1-6, got " + h.level());
                }
            }
            case DocumentBlock.WidgetBlock w -> {
                if (w.chartType() != null && !VALID_CHART_TYPES.contains(w.chartType())) {
                    errors.add("WidgetBlock has invalid chartType: " + w.chartType());
                }
            }
            case DocumentBlock.SwotTableBlock s -> {
                if (s.strengths().isEmpty() && s.weaknesses().isEmpty()
                    && s.opportunities().isEmpty() && s.threats().isEmpty()) {
                    errors.add("SwotTableBlock has all empty quadrants");
                }
            }
            case DocumentBlock.TableBlock t -> {
                int headerCount = t.headers().size();
                for (int i = 0; i < t.rows().size(); i++) {
                    if (t.rows().get(i).size() != headerCount) {
                        errors.add("TableBlock row %d has %d columns but headers have %d"
                            .formatted(i, t.rows().get(i).size(), headerCount));
                    }
                }
            }
            case DocumentBlock.EngineQuoteBlock eq -> {
                for (DocumentBlock inner : eq.blocks()) {
                    if (!(inner instanceof DocumentBlock.ParagraphBlock)
                        && !(inner instanceof DocumentBlock.ListBlock)) {
                        errors.add("EngineQuoteBlock contains non-paragraph/list block: " + inner.getClass().getSimpleName());
                    }
                }
            }
            default -> { /* no validation needed */ }
        }
    }
}

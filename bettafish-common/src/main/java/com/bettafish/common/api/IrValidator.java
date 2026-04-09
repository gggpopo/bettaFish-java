package com.bettafish.common.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class IrValidator {

    public record ValidationError(String path, String message) {}

    private static final Set<String> ALLOWED_CHART_TYPES =
            Set.of("bar", "line", "pie", "radar", "doughnut", "polarArea", "scatter");
    private static final Set<String> ALLOWED_CALLOUT_LEVELS =
            Set.of("info", "warn", "error", "success");

    private IrValidator() {}

    public static List<ValidationError> validate(DocumentIr ir) {
        if (ir == null || ir.blocks() == null) {
            return List.of(new ValidationError("", "DocumentIr or blocks is null"));
        }
        return validateBlocks(ir.blocks());
    }

    public static List<ValidationError> validateBlocks(List<DocumentBlock> blocks) {
        List<ValidationError> errors = new ArrayList<>();
        if (blocks == null) {
            return errors;
        }
        for (int i = 0; i < blocks.size(); i++) {
            validateBlock(blocks.get(i), "blocks[" + i + "]", errors);
        }
        return errors;
    }

    private static void validateBlock(DocumentBlock block, String path, List<ValidationError> errors) {
        switch (block) {
            case DocumentBlock.HeadingBlock b -> {
                if (b.level() < 1 || b.level() > 6) {
                    errors.add(new ValidationError(path, "heading level must be 1-6, got " + b.level()));
                }
                if (isBlank(b.text())) {
                    errors.add(new ValidationError(path, "heading text must not be blank"));
                }
            }
            case DocumentBlock.ParagraphBlock b -> {
                if (isBlank(b.text())) {
                    errors.add(new ValidationError(path, "paragraph text must not be blank"));
                }
            }
            case DocumentBlock.ListBlock b -> {
                if (b.items() == null || b.items().isEmpty()) {
                    errors.add(new ValidationError(path, "list items must not be empty"));
                }
            }
            case DocumentBlock.QuoteBlock b -> {
                if (isBlank(b.text())) {
                    errors.add(new ValidationError(path, "quote text must not be blank"));
                }
            }
            case DocumentBlock.TableBlock b -> {
                if (b.headers() == null || b.headers().isEmpty()) {
                    errors.add(new ValidationError(path, "table headers must not be empty"));
                }
                if (b.rows() != null && b.headers() != null) {
                    for (int i = 0; i < b.rows().size(); i++) {
                        if (b.rows().get(i).size() != b.headers().size()) {
                            errors.add(new ValidationError(path + ".rows[" + i + "]",
                                    "row length " + b.rows().get(i).size()
                                            + " does not match headers length " + b.headers().size()));
                        }
                    }
                }
            }
            case DocumentBlock.CodeBlock b -> {
                if (isBlank(b.code())) {
                    errors.add(new ValidationError(path, "code must not be blank"));
                }
            }
            case DocumentBlock.LinkBlock b -> {
                if (isBlank(b.href())) {
                    errors.add(new ValidationError(path, "link href must not be blank"));
                }
            }
            case DocumentBlock.WidgetBlock b -> {
                if (b.chartType() == null || !ALLOWED_CHART_TYPES.contains(b.chartType())) {
                    errors.add(new ValidationError(path, "widget chartType must be one of " + ALLOWED_CHART_TYPES + ", got " + b.chartType()));
                }
                if (b.config() == null || b.config().isEmpty()) {
                    errors.add(new ValidationError(path, "widget config must not be null or empty"));
                }
            }
            case DocumentBlock.CalloutBlock b -> {
                if (b.level() == null || !ALLOWED_CALLOUT_LEVELS.contains(b.level())) {
                    errors.add(new ValidationError(path, "callout level must be one of " + ALLOWED_CALLOUT_LEVELS + ", got " + b.level()));
                }
                if (isBlank(b.text())) {
                    errors.add(new ValidationError(path, "callout text must not be blank"));
                }
            }
            case DocumentBlock.KpiGridBlock b -> {
                if (b.items() == null || b.items().isEmpty()) {
                    errors.add(new ValidationError(path, "kpiGrid items must not be empty"));
                } else {
                    for (int i = 0; i < b.items().size(); i++) {
                        if (isBlank(b.items().get(i).label())) {
                            errors.add(new ValidationError(path + ".items[" + i + "]", "kpiGrid item label must not be blank"));
                        }
                    }
                }
            }
            case DocumentBlock.HrBlock ignored -> { /* no validation */ }
            case DocumentBlock.SwotTableBlock b -> {
                if (isEmpty(b.strengths()) && isEmpty(b.weaknesses()) && isEmpty(b.opportunities()) && isEmpty(b.threats())) {
                    errors.add(new ValidationError(path, "swotTable must have at least one non-empty quadrant"));
                }
            }
            case DocumentBlock.PestTableBlock b -> {
                if (isEmpty(b.political()) && isEmpty(b.economic()) && isEmpty(b.social()) && isEmpty(b.technological())) {
                    errors.add(new ValidationError(path, "pestTable must have at least one non-empty quadrant"));
                }
            }
            case DocumentBlock.EngineQuoteBlock b -> {
                if (isBlank(b.engine())) {
                    errors.add(new ValidationError(path, "engineQuote engine must not be blank"));
                }
                if (b.blocks() != null) {
                    for (int i = 0; i < b.blocks().size(); i++) {
                        DocumentBlock inner = b.blocks().get(i);
                        String innerPath = path + ".blocks[" + i + "]";
                        if (!(inner instanceof DocumentBlock.ParagraphBlock)) {
                            errors.add(new ValidationError(innerPath, "engineQuote blocks must only contain ParagraphBlock"));
                        }
                        validateBlock(inner, innerPath, errors);
                    }
                }
            }
            case DocumentBlock.MathBlock b -> {
                if (isBlank(b.latex())) {
                    errors.add(new ValidationError(path, "math latex must not be blank"));
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}

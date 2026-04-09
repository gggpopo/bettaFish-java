package com.bettafish.common.api;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DocumentBlock.HeadingBlock.class, name = "heading"),
    @JsonSubTypes.Type(value = DocumentBlock.ParagraphBlock.class, name = "paragraph"),
    @JsonSubTypes.Type(value = DocumentBlock.ListBlock.class, name = "list"),
    @JsonSubTypes.Type(value = DocumentBlock.QuoteBlock.class, name = "quote"),
    @JsonSubTypes.Type(value = DocumentBlock.TableBlock.class, name = "table"),
    @JsonSubTypes.Type(value = DocumentBlock.CodeBlock.class, name = "code"),
    @JsonSubTypes.Type(value = DocumentBlock.LinkBlock.class, name = "link"),
    @JsonSubTypes.Type(value = DocumentBlock.WidgetBlock.class, name = "widget"),
    @JsonSubTypes.Type(value = DocumentBlock.CalloutBlock.class, name = "callout"),
    @JsonSubTypes.Type(value = DocumentBlock.KpiGridBlock.class, name = "kpiGrid"),
    @JsonSubTypes.Type(value = DocumentBlock.HrBlock.class, name = "hr"),
    @JsonSubTypes.Type(value = DocumentBlock.SwotTableBlock.class, name = "swotTable"),
    @JsonSubTypes.Type(value = DocumentBlock.PestTableBlock.class, name = "pestTable"),
    @JsonSubTypes.Type(value = DocumentBlock.EngineQuoteBlock.class, name = "engineQuote"),
    @JsonSubTypes.Type(value = DocumentBlock.MathBlock.class, name = "math")
})
public sealed interface DocumentBlock permits
    DocumentBlock.HeadingBlock,
    DocumentBlock.ParagraphBlock,
    DocumentBlock.ListBlock,
    DocumentBlock.QuoteBlock,
    DocumentBlock.TableBlock,
    DocumentBlock.CodeBlock,
    DocumentBlock.LinkBlock,
    DocumentBlock.WidgetBlock,
    DocumentBlock.CalloutBlock,
    DocumentBlock.KpiGridBlock,
    DocumentBlock.HrBlock,
    DocumentBlock.SwotTableBlock,
    DocumentBlock.PestTableBlock,
    DocumentBlock.EngineQuoteBlock,
    DocumentBlock.MathBlock {

    record HeadingBlock(int level, String text) implements DocumentBlock {
    }

    record ParagraphBlock(String text) implements DocumentBlock {
    }

    record ListBlock(boolean ordered, List<String> items) implements DocumentBlock {
        public ListBlock {
            items = List.copyOf(items);
        }
    }

    record QuoteBlock(String text, String attribution) implements DocumentBlock {
    }

    record TableBlock(List<String> headers, List<List<String>> rows) implements DocumentBlock {
        public TableBlock {
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }

    record CodeBlock(String language, String code) implements DocumentBlock {
    }

    record LinkBlock(String text, String href) implements DocumentBlock {
    }

    record WidgetBlock(String chartType, Map<String, Object> config) implements DocumentBlock {
    }

    record CalloutBlock(String level, String text) implements DocumentBlock {
    }

    record KpiItem(String label, String value, String trend) {
    }

    record KpiGridBlock(List<KpiItem> items) implements DocumentBlock {
        public KpiGridBlock {
            items = List.copyOf(items);
        }
    }

    record HrBlock() implements DocumentBlock {
    }

    record SwotTableBlock(String title, List<String> strengths, List<String> weaknesses,
                          List<String> opportunities, List<String> threats) implements DocumentBlock {
        public SwotTableBlock {
            strengths = List.copyOf(strengths);
            weaknesses = List.copyOf(weaknesses);
            opportunities = List.copyOf(opportunities);
            threats = List.copyOf(threats);
        }
    }

    record PestTableBlock(String title, List<String> political, List<String> economic,
                          List<String> social, List<String> technological) implements DocumentBlock {
        public PestTableBlock {
            political = List.copyOf(political);
            economic = List.copyOf(economic);
            social = List.copyOf(social);
            technological = List.copyOf(technological);
        }
    }

    record EngineQuoteBlock(String engine, String title, List<DocumentBlock> blocks) implements DocumentBlock {
        public EngineQuoteBlock {
            blocks = List.copyOf(blocks);
        }
    }

    record MathBlock(String latex) implements DocumentBlock {
    }
}

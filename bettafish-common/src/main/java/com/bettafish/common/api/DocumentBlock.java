package com.bettafish.common.api;

import java.util.List;
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
    @JsonSubTypes.Type(value = DocumentBlock.LinkBlock.class, name = "link")
})
public sealed interface DocumentBlock permits
    DocumentBlock.HeadingBlock,
    DocumentBlock.ParagraphBlock,
    DocumentBlock.ListBlock,
    DocumentBlock.QuoteBlock,
    DocumentBlock.TableBlock,
    DocumentBlock.CodeBlock,
    DocumentBlock.LinkBlock {

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
}

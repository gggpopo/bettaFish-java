package com.bettafish.report.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.report.ir.ChapterGenerationResult;
import com.bettafish.report.ir.ChapterSpec;
import com.fasterxml.jackson.core.type.TypeReference;

class ChapterGenerationNodeTest {

    @Test
    void retriesWhenGeneratedChapterFailsDensityCheck() {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway(
            new ChapterGenerationNode.ChapterDraftResponse(List.of(
                new DocumentBlock.ParagraphBlock("too short")
            )),
            new ChapterGenerationNode.ChapterDraftResponse(List.of(
                new DocumentBlock.ParagraphBlock("这一章围绕查询引擎的结果展开，补齐了时间线、平台差异、情绪走向和关键证据来源，内容密度足够支撑章节落地。"),
                new DocumentBlock.ListBlock(false, List.of("补充平台差异", "补充证据来源"))
            ))
        );
        ChapterGenerationNode node = new ChapterGenerationNode(llmGateway, 2, 40);

        ChapterGenerationResult result = node.generate(
            "武汉大学樱花季舆情热度",
            new ChapterSpec("query", "Query analysis", "整合 QueryEngine 发现", "headline | summary | key points")
        );

        assertEquals(2, llmGateway.callCount());
        assertEquals(2, result.attemptsUsed());
        assertFalse(result.placeholder());
        assertInstanceOf(DocumentBlock.HeadingBlock.class, result.blocks().getFirst());
        assertTrue(result.blocks().stream().anyMatch(DocumentBlock.ListBlock.class::isInstance));
    }

    @Test
    void fallsBackToPlaceholderAfterRepeatedInvalidResults() {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway(
            new ChapterGenerationNode.ChapterDraftResponse(List.of()),
            new ChapterGenerationNode.ChapterDraftResponse(List.of(new DocumentBlock.ParagraphBlock("bad")))
        );
        ChapterGenerationNode node = new ChapterGenerationNode(llmGateway, 2, 50);

        ChapterGenerationResult result = node.generate(
            "武汉大学樱花季舆情热度",
            new ChapterSpec("forum", "Forum summary", "整合论坛主持结论", "overview | consensus")
        );

        assertEquals(2, llmGateway.callCount());
        assertTrue(result.placeholder());
        assertEquals(2, result.attemptsUsed());
        assertEquals("Forum summary", ((DocumentBlock.HeadingBlock) result.blocks().getFirst()).text());
        assertTrue(result.blocks().stream()
            .filter(DocumentBlock.ParagraphBlock.class::isInstance)
            .map(DocumentBlock.ParagraphBlock.class::cast)
            .anyMatch(block -> block.text().contains("占位")));
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final Deque<Object> responses = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();

        private RecordingLlmGateway(Object... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            prompts.add(userPrompt);
            return responseType.cast(responses.removeFirst());
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Class response type expected");
        }

        private int callCount() {
            return prompts.size();
        }
    }
}

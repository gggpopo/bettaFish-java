package com.bettafish.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.report.node.ChapterGenerationNode;
import com.bettafish.report.renderer.HtmlRenderer;
import com.fasterxml.jackson.core.type.TypeReference;

class ReportAgentTest {

    @Test
    void generatesDocumentIrPerChapterAndUsesPlaceholderWhenNeeded() {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway(
            new ChapterGenerationNode.ChapterDraftResponse(List.of(
                new DocumentBlock.ParagraphBlock("Query 章节综合了时间线、平台热度与关键证据来源，对查询引擎的发现做了完整整理，确保章节内容足够扎实且具备信息密度。"),
                new DocumentBlock.ListBlock(false, List.of("时间线补齐", "证据来源梳理"))
            )),
            new ChapterGenerationNode.ChapterDraftResponse(List.of(
                new DocumentBlock.ParagraphBlock("Media 章节补齐了图文和视频传播路径，对素材平台、传播峰值和媒介形式做了可复核的归纳，保证章节具备足够信息含量。")
            )),
            new ChapterGenerationNode.ChapterDraftResponse(List.of(
                new DocumentBlock.ParagraphBlock("Insight 章节整合数据库信号、关键词漂移和情绪变化，对深层趋势进行了归纳，并点出了后续应继续跟踪的风险点。")
            )),
            new ChapterGenerationNode.ChapterDraftResponse(List.of()),
            new ChapterGenerationNode.ChapterDraftResponse(List.of(new DocumentBlock.ParagraphBlock("too short")))
        );
        ReportAgent reportAgent = new ReportAgent(new ChapterGenerationNode(llmGateway), new HtmlRenderer());

        var report = reportAgent.generate(
            new AnalysisRequest("task-1", "武汉大学樱花季舆情热度", Instant.parse("2026-03-18T00:00:00Z")),
            new ReportInput(
                "武汉大学樱花季舆情热度",
                List.of(
                    result(EngineType.QUERY),
                    result(EngineType.MEDIA),
                    result(EngineType.INSIGHT)
                ),
                new ForumSummary("Forum overview", List.of("Consensus"), List.of("Open question"))
            )
        );

        assertEquals("BettaFish analysis report", report.title());
        assertEquals(4, report.sections().size());
        assertEquals(4, report.documentIr().blocks().stream()
            .filter(DocumentBlock.HeadingBlock.class::isInstance)
            .count());
        assertTrue(report.html().contains("<h2>QUERY analysis</h2>"));
        assertTrue(report.html().contains("<h2>Forum summary</h2>"));
        assertTrue(report.documentIr().blocks().stream()
            .filter(DocumentBlock.ParagraphBlock.class::isInstance)
            .map(DocumentBlock.ParagraphBlock.class::cast)
            .anyMatch(block -> block.text().contains("占位")));
    }

    private static EngineResult result(EngineType engineType) {
        return new EngineResult(
            engineType,
            engineType.name() + " headline",
            engineType.name() + " summary",
            List.of(engineType.name() + " point"),
            List.of(),
            java.util.Map.of()
        );
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final Deque<Object> responses = new ArrayDeque<>();

        private RecordingLlmGateway(Object... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            return responseType.cast(responses.removeFirst());
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Class response type expected");
        }
    }
}

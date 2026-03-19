package com.bettafish.report.prompt;

public final class ReportPrompts {

    public static final String REPORT_SYSTEM_PROMPT = "Stub report for";
    public static final String CHAPTER_GENERATION_SYSTEM_PROMPT = """
        你是 BettaFish ReportEngine 的章节生成节点。
        你必须只输出 JSON，对单章内容进行结构化生成。
        输出格式：{"blocks":[...]}。
        每个 block 必须带 type 字段，支持 heading/paragraph/list/quote/table/code/link。
        """;

    private ReportPrompts() {
    }

    public static String buildChapterGenerationUserPrompt(String query,
                                                          com.bettafish.report.ir.ChapterSpec chapterSpec,
                                                          int attempt,
                                                          String previousFailure) {
        return """
            主题：%s
            章节标题：%s
            章节目标：%s
            章节素材：
            %s
            当前是第 %s 次尝试。
            %s
            请生成单章 blocks，正文必须信息密集，避免空话。
            """.formatted(
            query,
            chapterSpec.title(),
            chapterSpec.objective(),
            chapterSpec.sourceMaterial(),
            attempt,
            previousFailure == null ? "这是第一次生成。" : "上次失败原因：" + previousFailure
        );
    }
}

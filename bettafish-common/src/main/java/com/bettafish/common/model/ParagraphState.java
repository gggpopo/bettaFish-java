package com.bettafish.common.model;

import java.util.ArrayList;
import java.util.List;
import com.bettafish.common.api.SourceReference;

public class ParagraphState {

    private String paragraphId;
    private String title;
    private String expectedContent;
    private String currentDraft = "";
    private String finalConclusion = "";
    private boolean completed;
    private int reflectionRoundsCompleted;
    private int forumGuidanceRevisionApplied;
    private String forumGuidancePrompt = "";
    private final List<String> currentKeyPoints = new ArrayList<>();
    private final List<String> currentEvidenceGaps = new ArrayList<>();
    private final List<SearchRecord> searchHistory = new ArrayList<>();

    public ParagraphState() {
    }

    public ParagraphState(String paragraphId, String title, String expectedContent) {
        this.paragraphId = paragraphId;
        this.title = title;
        this.expectedContent = expectedContent;
    }

    public String getParagraphId() {
        return paragraphId;
    }

    public void setParagraphId(String paragraphId) {
        this.paragraphId = paragraphId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExpectedContent() {
        return expectedContent;
    }

    public void setExpectedContent(String expectedContent) {
        this.expectedContent = expectedContent;
    }

    public String getCurrentDraft() {
        return currentDraft;
    }

    public void setCurrentDraft(String currentDraft) {
        this.currentDraft = currentDraft;
    }

    public String getFinalConclusion() {
        return finalConclusion;
    }

    public void setFinalConclusion(String finalConclusion) {
        this.finalConclusion = finalConclusion;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getReflectionRoundsCompleted() {
        return reflectionRoundsCompleted;
    }

    public void setReflectionRoundsCompleted(int reflectionRoundsCompleted) {
        this.reflectionRoundsCompleted = reflectionRoundsCompleted;
    }

    public int getForumGuidanceRevisionApplied() {
        return forumGuidanceRevisionApplied;
    }

    public void setForumGuidanceRevisionApplied(int forumGuidanceRevisionApplied) {
        this.forumGuidanceRevisionApplied = forumGuidanceRevisionApplied;
    }

    public String getForumGuidancePrompt() {
        return forumGuidancePrompt;
    }

    public void setForumGuidancePrompt(String forumGuidancePrompt) {
        this.forumGuidancePrompt = forumGuidancePrompt;
    }

    public List<String> getCurrentKeyPoints() {
        return currentKeyPoints;
    }

    public void setCurrentKeyPoints(List<String> currentKeyPoints) {
        this.currentKeyPoints.clear();
        if (currentKeyPoints != null) {
            this.currentKeyPoints.addAll(currentKeyPoints);
        }
    }

    public List<String> getCurrentEvidenceGaps() {
        return currentEvidenceGaps;
    }

    public void setCurrentEvidenceGaps(List<String> currentEvidenceGaps) {
        this.currentEvidenceGaps.clear();
        if (currentEvidenceGaps != null) {
            this.currentEvidenceGaps.addAll(currentEvidenceGaps);
        }
    }

    public List<SearchRecord> getSearchHistory() {
        return searchHistory;
    }

    public void addSearchRecord(SearchRecord searchRecord) {
        searchHistory.add(searchRecord);
    }

    public static class SearchRecord {

        private String toolName;
        private String searchQuery;
        private String reasoning;
        private int roundIndex;
        private final List<SourceReference> sources = new ArrayList<>();

        public SearchRecord() {
        }

        public SearchRecord(String toolName, String searchQuery, String reasoning, int roundIndex,
                            List<SourceReference> sources) {
            this.toolName = toolName;
            this.searchQuery = searchQuery;
            this.reasoning = reasoning;
            this.roundIndex = roundIndex;
            this.sources.addAll(sources);
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getSearchQuery() {
            return searchQuery;
        }

        public void setSearchQuery(String searchQuery) {
            this.searchQuery = searchQuery;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public int getRoundIndex() {
            return roundIndex;
        }

        public void setRoundIndex(int roundIndex) {
            this.roundIndex = roundIndex;
        }

        public List<SourceReference> getSources() {
            return sources;
        }
    }
}

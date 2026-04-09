package com.bettafish.insight.tool;

import com.bettafish.common.api.SourceReference;
import com.bettafish.common.config.DatabaseSearchProperties;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MediaCrawlerDbTool {

    private static final Logger log = LoggerFactory.getLogger(MediaCrawlerDbTool.class);

    // Hotness weights matching target project
    private static final double W_LIKE = 1.0;
    private static final double W_COMMENT = 5.0;
    private static final double W_SHARE = 10.0;
    private static final double W_VIEW = 0.1;
    private static final double W_DANMAKU = 0.5;

    private static final Map<String, String> CONTENT_TABLES = Map.of(
        "bilibili", "bilibili_video",
        "weibo", "weibo_note",
        "douyin", "douyin_aweme",
        "kuaishou", "kuaishou_video",
        "xhs", "xhs_note",
        "zhihu", "zhihu_content",
        "tieba", "tieba_post"
    );

    private static final Map<String, String> COMMENT_TABLES = Map.of(
        "bilibili", "bilibili_video_comment",
        "weibo", "weibo_note_comment",
        "douyin", "douyin_aweme_comment",
        "kuaishou", "kuaishou_video_comment",
        "xhs", "xhs_note_comment",
        "zhihu", "zhihu_content_comment",
        "tieba", "tieba_post_comment"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSearchProperties properties;

    public MediaCrawlerDbTool(JdbcTemplate jdbcTemplate, DatabaseSearchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    // Package-private constructor for testing without DB
    MediaCrawlerDbTool(DatabaseSearchProperties properties) {
        this.jdbcTemplate = null;
        this.properties = properties;
    }

    /** Original interface method — delegates to searchTopicGlobally */
    public List<SourceReference> search(String query, List<String> keywords) {
        checkCancellation();
        if (!properties.isEnabled() || jdbcTemplate == null) {
            log.info("Database search disabled, returning stub result");
            return List.of(new SourceReference(
                "Media crawler DB stub result",
                "https://example.com/insight",
                "Database search disabled. Query: " + query + ", keywords: " + String.join(", ", keywords)
            ));
        }
        List<QueryResult> results = searchTopicGlobally(query, keywords, properties.getDefaultLimitPerTable());
        return results.stream()
            .map(r -> new SourceReference(
                truncate(r.titleOrContent(), 100),
                r.url() != null ? r.url() : "",
                formatSnippet(r)))
            .toList();
    }

    /** Search hot content across all platforms, sorted by hotness */
    public List<QueryResult> searchHotContent(String timePeriod, int limit) {
        checkCancellation();
        if (!properties.isEnabled() || jdbcTemplate == null) return List.of();

        Instant since = calculateSince(timePeriod);
        List<QueryResult> allResults = new ArrayList<>();

        for (var entry : CONTENT_TABLES.entrySet()) {
            String platform = entry.getKey();
            String table = entry.getValue();
            try {
                String sql = "SELECT * FROM " + table + " WHERE create_time >= ? ORDER BY "
                    + hotnessSql() + " DESC LIMIT ?";
                var rows = jdbcTemplate.queryForList(sql, java.sql.Timestamp.from(since), limit);
                allResults.addAll(mapRows(rows, platform, table, null));
            } catch (Exception e) {
                log.warn("Failed to query hot content from {}: {}", table, e.getMessage());
            }
        }

        allResults.sort(Comparator.comparingDouble(QueryResult::hotnessScore).reversed());
        return allResults.stream().limit(limit).toList();
    }

    /** Search topic globally across all content + comment tables */
    public List<QueryResult> searchTopicGlobally(String query, List<String> keywords, int limitPerTable) {
        checkCancellation();
        if (!properties.isEnabled() || jdbcTemplate == null) return List.of();

        List<QueryResult> allResults = new ArrayList<>();
        String likePattern = "%" + query + "%";

        // Search content tables
        for (var entry : CONTENT_TABLES.entrySet()) {
            allResults.addAll(searchTable(entry.getKey(), entry.getValue(), likePattern, keywords, limitPerTable));
        }
        // Search comment tables
        for (var entry : COMMENT_TABLES.entrySet()) {
            allResults.addAll(searchTable(entry.getKey(), entry.getValue(), likePattern, keywords, limitPerTable));
        }

        return dedup(allResults);
    }

    /** Search topic by date range */
    public List<QueryResult> searchTopicByDate(String query, List<String> keywords,
                                                LocalDate startDate, LocalDate endDate, int limitPerTable) {
        checkCancellation();
        if (!properties.isEnabled() || jdbcTemplate == null) return List.of();

        List<QueryResult> allResults = new ArrayList<>();
        String likePattern = "%" + query + "%";
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        for (var entry : CONTENT_TABLES.entrySet()) {
            allResults.addAll(searchTableWithDate(entry.getKey(), entry.getValue(), likePattern, keywords, start, end, limitPerTable));
        }
        for (var entry : COMMENT_TABLES.entrySet()) {
            allResults.addAll(searchTableWithDate(entry.getKey(), entry.getValue(), likePattern, keywords, start, end, limitPerTable));
        }

        return dedup(allResults);
    }

    /** Get comments for a topic */
    public List<QueryResult> getCommentsForTopic(String query, List<String> keywords, int limit) {
        checkCancellation();
        if (!properties.isEnabled() || jdbcTemplate == null) return List.of();

        List<QueryResult> allResults = new ArrayList<>();
        String likePattern = "%" + query + "%";
        int perTable = Math.max(1, limit / COMMENT_TABLES.size());

        for (var entry : COMMENT_TABLES.entrySet()) {
            allResults.addAll(searchTable(entry.getKey(), entry.getValue(), likePattern, keywords, perTable));
        }

        return dedup(allResults).stream().limit(limit).toList();
    }

    /** Search topic on a specific platform */
    public List<QueryResult> searchTopicOnPlatform(String query, List<String> keywords, String platform,
                                                    LocalDate startDate, LocalDate endDate, int limit) {
        checkCancellation();
        if (!properties.isEnabled() || jdbcTemplate == null) return List.of();

        String contentTable = CONTENT_TABLES.get(platform);
        String commentTable = COMMENT_TABLES.get(platform);
        if (contentTable == null) {
            log.warn("Unknown platform: {}", platform);
            return List.of();
        }

        List<QueryResult> allResults = new ArrayList<>();
        String likePattern = "%" + query + "%";

        if (startDate != null && endDate != null) {
            Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            allResults.addAll(searchTableWithDate(platform, contentTable, likePattern, keywords, start, end, limit));
            if (commentTable != null) {
                allResults.addAll(searchTableWithDate(platform, commentTable, likePattern, keywords, start, end, limit));
            }
        } else {
            allResults.addAll(searchTable(platform, contentTable, likePattern, keywords, limit));
            if (commentTable != null) {
                allResults.addAll(searchTable(platform, commentTable, likePattern, keywords, limit));
            }
        }

        return dedup(allResults);
    }

    // --- Private helpers ---

    private List<QueryResult> searchTable(String platform, String table, String likePattern,
                                           List<String> keywords, int limit) {
        try {
            // Build WHERE clause: match query OR any keyword in title/content/desc
            String sql = "SELECT * FROM " + table + " WHERE (title LIKE ? OR content LIKE ? OR desc LIKE ?) LIMIT ?";
            var rows = jdbcTemplate.queryForList(sql, likePattern, likePattern, likePattern, limit);
            return mapRows(rows, platform, table, likePattern);
        } catch (Exception e) {
            log.debug("Query on {} failed (table may not exist or have different schema): {}", table, e.getMessage());
            // Try simpler query without 'desc' column
            try {
                String sql = "SELECT * FROM " + table + " WHERE (title LIKE ? OR content LIKE ?) LIMIT ?";
                var rows = jdbcTemplate.queryForList(sql, likePattern, likePattern, limit);
                return mapRows(rows, platform, table, likePattern);
            } catch (Exception e2) {
                log.warn("Failed to query {}: {}", table, e2.getMessage());
                return List.of();
            }
        }
    }

    private List<QueryResult> searchTableWithDate(String platform, String table, String likePattern,
                                                   List<String> keywords, Instant start, Instant end, int limit) {
        try {
            String sql = "SELECT * FROM " + table
                + " WHERE (title LIKE ? OR content LIKE ?) AND create_time >= ? AND create_time < ? LIMIT ?";
            var rows = jdbcTemplate.queryForList(sql,
                likePattern, likePattern, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), limit);
            return mapRows(rows, platform, table, likePattern);
        } catch (Exception e) {
            log.warn("Date-range query on {} failed: {}", table, e.getMessage());
            return List.of();
        }
    }

    private List<QueryResult> mapRows(List<Map<String, Object>> rows, String platform, String table, String keyword) {
        int maxLen = properties.getMaxContentLength();
        return rows.stream().map(row -> {
            String title = getStr(row, "title");
            String content = getStr(row, "content");
            String text = title != null && !title.isBlank() ? title : content;
            text = truncate(text, maxLen);

            int likes = getInt(row, "liked_count", "like_count", "digg_count");
            int comments = getInt(row, "comment_count", "comments_count");
            int shares = getInt(row, "share_count", "shared_count", "forward_count");
            int views = getInt(row, "view_count", "play_count", "read_count");
            int danmaku = getInt(row, "danmaku_count");

            double hotness = likes * W_LIKE + comments * W_COMMENT + shares * W_SHARE
                + views * W_VIEW + danmaku * W_DANMAKU;

            return new QueryResult(
                platform,
                table.contains("comment") ? "comment" : "content",
                text,
                getStr(row, "nickname", "user_nickname", "author"),
                getStr(row, "url", "note_url", "video_url"),
                parseTimestamp(row.get("create_time")),
                Map.of("like", likes, "comment", comments, "share", shares, "view", views, "danmaku", danmaku),
                hotness,
                keyword,
                table
            );
        }).toList();
    }

    private static String hotnessSql() {
        return "(COALESCE(liked_count,0)*1.0 + COALESCE(comment_count,0)*5.0 + COALESCE(share_count,0)*10.0 + COALESCE(view_count,0)*0.1)";
    }

    private static Instant calculateSince(String timePeriod) {
        return switch (timePeriod) {
            case "24h" -> Instant.now().minus(Duration.ofHours(24));
            case "week" -> Instant.now().minus(Duration.ofDays(7));
            case "year" -> Instant.now().minus(Duration.ofDays(365));
            default -> Instant.now().minus(Duration.ofHours(24));
        };
    }

    private static String getStr(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object val = row.get(key);
            if (val != null) return val.toString();
        }
        return "";
    }

    private static int getInt(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object val = row.get(key);
            if (val instanceof Number n) return n.intValue();
        }
        return 0;
    }

    private static Instant parseTimestamp(Object val) {
        if (val instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (val instanceof java.util.Date d) return d.toInstant();
        if (val instanceof Number n) {
            long v = n.longValue();
            return v > 1_000_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
        }
        return null;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private static String formatSnippet(QueryResult r) {
        return "[%s/%s] %s | likes:%d comments:%d".formatted(
            r.platform(), r.contentType(), r.authorNickname(),
            r.engagement().getOrDefault("like", 0),
            r.engagement().getOrDefault("comment", 0));
    }

    private static List<QueryResult> dedup(List<QueryResult> results) {
        Set<String> seen = new HashSet<>();
        return results.stream()
            .filter(r -> {
                String key = r.url() != null && !r.url().isBlank() ? r.url() : r.titleOrContent();
                return seen.add(key);
            })
            .toList();
    }

    private static void checkCancellation() {
        ExecutionContext ctx = ExecutionContextHolder.current();
        if (ctx != null) ctx.throwIfCancellationRequested();
    }
}

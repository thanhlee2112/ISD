package com.example.CMS.DTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Article as sent to the frontend. The raw database id is deliberately not exposed;
 * clients refer to articles by {@code encryptedId}.
 */
public record ArticleDto(
        String encryptedId,
        String title,
        String summary,
        String content,
        String authorName,
        String reviewerName,
        int status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String notes,
        String comments,
        List<ViewRecord> viewsList) {

    /** One day of view counts; {@code date} is formatted YYYY-MM-DD. */
    public record ViewRecord(String date, int numberOfViews) {
    }
}

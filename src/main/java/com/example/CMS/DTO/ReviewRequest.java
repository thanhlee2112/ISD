package com.example.CMS.DTO;

import com.example.CMS.Common.GlobalConstants.Status;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editor's review: optional edits to the article, notes, a comment and an optional decision. */
public class ReviewRequest {
    /** Encrypted article id, as returned by the list/detail endpoints. */
    @NotBlank
    private String id;

    @Valid
    private ReviewedArticle article;

    private String notes;
    private String comments;

    /** Optional top-level decision; {@code article.status} takes precedence. */
    private Status status;

    public static class ReviewedArticle {
        @Size(max = 255)
        private String title;

        @Size(max = 255)
        @JsonProperty("abstract")
        @JsonAlias("summary")
        private String articleAbstract;

        private String content;

        /** Decision: UNDER_REVIEW (2), PUBLISHED (3) or RETURNED (4). */
        private Status status;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getArticleAbstract() { return articleAbstract; }
        public void setArticleAbstract(String articleAbstract) { this.articleAbstract = articleAbstract; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ReviewedArticle getArticle() { return article; }
    public void setArticle(ReviewedArticle article) { this.article = article; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    /** The decision requested by the editor, or null if only a comment/notes were submitted. */
    public Status decision() {
        return article != null && article.getStatus() != null ? article.getStatus() : status;
    }
}

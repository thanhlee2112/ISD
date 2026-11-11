package com.example.CMS.DTO;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Writable fields of an article. Author, status and timestamps are never client-controlled
 * (status follows the endpoint used). {@code id} is the encrypted id when editing an existing article.
 * Unspecified/blank fields are allowed here because drafts may be incomplete; sending to the editors
 * is stricter and is checked in the service.
 */
public class ArticleRequest {
    private String id;

    @Size(max = 255)
    private String title;

    @Size(max = 255)
    @JsonProperty("abstract")
    @JsonAlias("summary")
    private String articleAbstract;

    private String content;

    private String notes;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArticleAbstract() { return articleAbstract; }
    public void setArticleAbstract(String articleAbstract) { this.articleAbstract = articleAbstract; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

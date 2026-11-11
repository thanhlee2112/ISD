package com.example.CMS.Model;

import com.example.CMS.Common.GlobalConstants.Status;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Article")
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ArticleID;

    @Column(nullable = false)
    private String Title;

    @Column(nullable = false)
    private String Abstract;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String Content;

    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private Status status;
    @Column
    private LocalDateTime CreatedAt;
    @Column
    private LocalDateTime UpdatedAt;

    @ManyToOne
    @JoinColumn(name="UserId", nullable = false)
    private User user;

    /** Free-form notes shared between writer and editors. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String notes;

    /** Editor who last reviewed the article. */
    @ManyToOne
    @JoinColumn(name = "ReviewerId")
    private User reviewer;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (CreatedAt == null) {
            CreatedAt = now;
        }
        UpdatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        UpdatedAt = LocalDateTime.now();
    }

    public Long getArticleID() {
        return ArticleID;
    }

    public void setArticleID(Long articleID) {
        ArticleID = articleID;
    }

    public String getTitle() {
        return Title;
    }

    public void setTitle(String title) {
        Title = title;
    }

    public String getAbstract() {
        return Abstract;
    }

    public void setAbstract(String anAbstract) {
        Abstract = anAbstract;
    }

    public String getContent() {
        return Content;
    }

    public void setContent(String content) {
        Content = content;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return CreatedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return UpdatedAt;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        CreatedAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        UpdatedAt = updatedAt;
    }
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }
}

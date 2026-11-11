package com.example.CMS.Model;

import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name="Reviews")
public class Reviews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ReviewId;

    @ManyToOne
    @JoinColumn(name="ArticleId")
    private Article article;
    @ManyToOne
    @JoinColumn(name="UserId")
    private User user;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column
    private LocalDateTime reviewedAt;

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public Long getReviewId() {
        return ReviewId;
    }

    public void setReviewId(Long reviewId) {
        ReviewId = reviewId;
    }
}

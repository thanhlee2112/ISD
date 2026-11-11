package com.example.CMS.Model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Number of views an article received on a given day; feeds the dashboards' charts. */
@Entity
@Table(name = "article_views")
public class ArticleView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ArticleId", nullable = false)
    private Article article;

    @Column(nullable = false)
    private LocalDate viewDate;

    @Column(nullable = false)
    private int numberOfViews;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Article getArticle() { return article; }
    public void setArticle(Article article) { this.article = article; }

    public LocalDate getViewDate() { return viewDate; }
    public void setViewDate(LocalDate viewDate) { this.viewDate = viewDate; }

    public int getNumberOfViews() { return numberOfViews; }
    public void setNumberOfViews(int numberOfViews) { this.numberOfViews = numberOfViews; }
}

package com.example.CMS.Services;

import com.example.CMS.Common.GlobalConstants.Status;
import com.example.CMS.Common.IdEncryptor;
import com.example.CMS.DTO.ReviewRequest;
import com.example.CMS.Exception.ConflictException;
import com.example.CMS.Exception.NotFoundException;
import com.example.CMS.Model.Article;
import com.example.CMS.Model.Reviews;
import com.example.CMS.Model.User;
import com.example.CMS.Repository.ArticleRepository;
import com.example.CMS.Repository.ReviewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ReviewsService {
    private static final Set<Status> DECISIONS = Set.of(Status.UNDER_REVIEW, Status.PUBLISHED, Status.RETURNED);

    private final ReviewsRepository reviewsRepository;
    private final ArticleRepository articleRepository;
    private final IdEncryptor idEncryptor;

    public ReviewsService(ReviewsRepository reviewsRepository, ArticleRepository articleRepository,
                          IdEncryptor idEncryptor) {
        this.reviewsRepository = reviewsRepository;
        this.articleRepository = articleRepository;
        this.idEncryptor = idEncryptor;
    }

    /**
     * Records an editor's review of an article that is waiting for review: optionally applies the
     * editor's corrections and notes, stores the comment, and moves the article to the requested status.
     */
    @Transactional
    public void review(User reviewer, ReviewRequest request) {
        Article article = articleRepository.findById(idEncryptor.decrypt(request.getId()))
                .filter(a -> a.getStatus() != Status.DRAFT) // drafts are private to their writer
                .orElseThrow(() -> new NotFoundException("Article not found!"));
        if (article.getStatus() != Status.PENDING && article.getStatus() != Status.UNDER_REVIEW) {
            throw new ConflictException("This article has already been reviewed");
        }

        Status decision = request.decision();
        if (decision != null && !DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("status must be one of: 2 (under review), 3 (published), 4 (returned)");
        }

        ReviewRequest.ReviewedArticle edits = request.getArticle();
        if (edits != null) {
            ArticleService.requireShort(edits.getTitle(), "title");
            ArticleService.requireShort(edits.getArticleAbstract(), "abstract");
            if (!ArticleService.isBlank(edits.getTitle())) article.setTitle(edits.getTitle());
            if (!ArticleService.isBlank(edits.getArticleAbstract())) article.setAbstract(edits.getArticleAbstract());
            if (!ArticleService.isBlank(edits.getContent())) article.setContent(edits.getContent());
        }
        if (request.getNotes() != null) {
            article.setNotes(request.getNotes());
        }
        if (decision == Status.PUBLISHED && (ArticleService.isBlank(article.getTitle())
                || ArticleService.isBlank(article.getAbstract()) || ArticleService.isBlank(article.getContent()))) {
            throw new IllegalArgumentException("title, summary and content are required to publish");
        }
        if (decision != null) {
            article.setStatus(decision);
        }
        article.setReviewer(reviewer);
        articleRepository.save(article);

        Reviews review = new Reviews();
        review.setArticle(article);
        review.setUser(reviewer);
        review.setComments(request.getComments());
        review.setReviewedAt(LocalDateTime.now());
        reviewsRepository.save(review);
    }
}

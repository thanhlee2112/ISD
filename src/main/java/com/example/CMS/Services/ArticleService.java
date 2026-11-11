package com.example.CMS.Services;

import com.example.CMS.Common.GlobalConstants.Status;
import com.example.CMS.Common.IdEncryptor;
import com.example.CMS.DTO.ArticleDto;
import com.example.CMS.DTO.ArticleDto.ViewRecord;
import com.example.CMS.DTO.ArticleRequest;
import com.example.CMS.Exception.ConflictException;
import com.example.CMS.Exception.NotFoundException;
import com.example.CMS.Model.Article;
import com.example.CMS.Model.ArticleView;
import com.example.CMS.Model.User;
import com.example.CMS.Repository.ArticleRepository;
import com.example.CMS.Repository.ArticleViewRepository;
import com.example.CMS.Repository.ReviewsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArticleService {
    private static final int MAX_SHORT_TEXT = 255;

    private final ArticleRepository articleRepository;
    private final ArticleViewRepository viewRepository;
    private final ReviewsRepository reviewsRepository;
    private final IdEncryptor idEncryptor;

    public ArticleService(ArticleRepository articleRepository, ArticleViewRepository viewRepository,
                          ReviewsRepository reviewsRepository, IdEncryptor idEncryptor) {
        this.articleRepository = articleRepository;
        this.viewRepository = viewRepository;
        this.reviewsRepository = reviewsRepository;
        this.idEncryptor = idEncryptor;
    }

    // ---- Lists ---------------------------------------------------------------------------

    /** Admin: every article. */
    @Transactional(readOnly = true)
    public List<ArticleDto> listAll() {
        return toDtos(articleRepository.findAll());
    }

    /** Editor: everything that has been sent to the editors (writers' private drafts stay hidden). */
    @Transactional(readOnly = true)
    public List<ArticleDto> listForEditor() {
        return toDtos(articleRepository.findByStatusNot(Status.DRAFT));
    }

    /** Writer: only their own articles. */
    @Transactional(readOnly = true)
    public List<ArticleDto> listForWriter(User writer) {
        return toDtos(articleRepository.findByUser(writer));
    }

    // ---- Details -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ArticleDto detailForAdmin(String encryptedId) {
        return toDetailDto(find(encryptedId));
    }

    @Transactional(readOnly = true)
    public ArticleDto detailForEditor(String encryptedId) {
        Article article = find(encryptedId);
        if (article.getStatus() == Status.DRAFT) {
            throw new NotFoundException("Article not found!");
        }
        return toDetailDto(article);
    }

    @Transactional(readOnly = true)
    public ArticleDto detailForWriter(String encryptedId, User writer) {
        return toDetailDto(findOwned(encryptedId, writer));
    }

    // ---- Writer actions ------------------------------------------------------------------

    /**
     * Creates a new article, or updates the writer's own one when {@code request.id} is set.
     * Saving as a draft allows incomplete fields; sending to the editors requires the resulting article to have
     * a title, summary and content.
     *
     * @return the article's encrypted id
     */
    @Transactional
    public String save(ArticleRequest request, User writer, boolean sendToEditor) {
        Article article;
        if (request.getId() != null && !request.getId().isBlank()) {
            article = findOwned(request.getId(), writer);
            if (article.getStatus() != Status.DRAFT && article.getStatus() != Status.RETURNED) {
                throw new ConflictException("This article can no longer be edited");
            }
        } else {
            article = new Article();
            article.setUser(writer);
            article.setCreatedAt(LocalDateTime.now());
            article.setTitle("");
            article.setAbstract("");
            article.setContent("");
        }

        // Only overwrite what the client actually sent, so a partial save never wipes a field.
        if (request.getTitle() != null) article.setTitle(request.getTitle());
        if (request.getArticleAbstract() != null) article.setAbstract(request.getArticleAbstract());
        if (request.getContent() != null) article.setContent(request.getContent());
        if (request.getNotes() != null) article.setNotes(request.getNotes());
        if (sendToEditor && (isBlank(article.getTitle()) || isBlank(article.getAbstract())
                || isBlank(article.getContent()))) {
            throw new IllegalArgumentException("title, summary and content are required to send to the editors");
        }
        article.setStatus(sendToEditor ? Status.PENDING : Status.DRAFT);

        return idEncryptor.encrypt(articleRepository.save(article).getArticleID());
    }

    // ---- Helpers -------------------------------------------------------------------------

    public static void requireShort(String value, String field) {
        if (value != null && value.length() > MAX_SHORT_TEXT) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_SHORT_TEXT + " characters");
        }
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    Article find(String encryptedId) {
        return articleRepository.findById(idEncryptor.decrypt(encryptedId))
                .orElseThrow(() -> new NotFoundException("Article not found!"));
    }

    /** Looks up an article that must belong to {@code writer}; others get "not found", not "forbidden". */
    private Article findOwned(String encryptedId, User writer) {
        Article article = find(encryptedId);
        if (article.getUser() == null || !article.getUser().getUserId().equals(writer.getUserId())) {
            throw new NotFoundException("Article not found!");
        }
        return article;
    }

    private List<ArticleDto> toDtos(List<Article> articles) {
        Map<Long, List<ViewRecord>> views = articles.isEmpty() ? Map.of() : viewsByArticle(articles);
        return articles.stream()
                .sorted(Comparator.comparing(Article::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> toDto(a, views.getOrDefault(a.getArticleID(), List.of()), null))
                .toList();
    }

    private ArticleDto toDetailDto(Article article) {
        String comments = reviewsRepository.findFirstByArticleOrderByReviewedAtDesc(article)
                .map(r -> r.getComments()).orElse(null);
        return toDto(article, viewsByArticle(List.of(article)).getOrDefault(article.getArticleID(), List.of()),
                comments);
    }

    private Map<Long, List<ViewRecord>> viewsByArticle(Collection<Article> articles) {
        return viewRepository.findByArticleIn(articles).stream()
                .sorted(Comparator.comparing(ArticleView::getViewDate))
                .collect(Collectors.groupingBy(v -> v.getArticle().getArticleID(),
                        Collectors.mapping(v -> new ViewRecord(v.getViewDate().toString(), v.getNumberOfViews()),
                                Collectors.toList())));
    }

    private ArticleDto toDto(Article a, List<ViewRecord> views, String comments) {
        return new ArticleDto(
                idEncryptor.encrypt(a.getArticleID()),
                a.getTitle(),
                a.getAbstract(),
                a.getContent(),
                a.getUser() != null ? a.getUser().getName() : null,
                a.getReviewer() != null ? a.getReviewer().getName() : null,
                a.getStatus().ordinal(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getNotes(),
                comments,
                views);
    }
}

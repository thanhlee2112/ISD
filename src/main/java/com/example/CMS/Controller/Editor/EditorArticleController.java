package com.example.CMS.Controller.Editor;

import com.example.CMS.Common.ResponseUtils;
import com.example.CMS.DTO.IdRequest;
import com.example.CMS.DTO.ReviewRequest;
import com.example.CMS.Model.CustomUserDetails;
import com.example.CMS.Services.ArticleService;
import com.example.CMS.Services.ReviewsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/editor/article")
public class EditorArticleController {

    private final ArticleService articleService;
    private final ReviewsService reviewsService;

    public EditorArticleController(ArticleService articleService, ReviewsService reviewsService) {
        this.articleService = articleService;
        this.reviewsService = reviewsService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> index() {
        return ResponseUtils.success(articleService.listForEditor());
    }

    @PostMapping("/detail")
    public ResponseEntity<?> detail(@Valid @RequestBody IdRequest request) {
        return ResponseUtils.success(articleService.detailForEditor(request.getId()));
    }

    @PostMapping("/review")
    public ResponseEntity<?> review(@Valid @RequestBody ReviewRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        reviewsService.review(principal.getUser(), request);
        return ResponseUtils.success("Reviewed successfully!");
    }
}

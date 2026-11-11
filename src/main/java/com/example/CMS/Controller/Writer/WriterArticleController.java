package com.example.CMS.Controller.Writer;

import com.example.CMS.Common.ResponseUtils;
import com.example.CMS.DTO.ArticleRequest;
import com.example.CMS.DTO.IdRequest;
import com.example.CMS.Model.CustomUserDetails;
import com.example.CMS.Services.ArticleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/writer/article")
public class WriterArticleController {

    private final ArticleService articleService;

    public WriterArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseUtils.success(articleService.listForWriter(principal.getUser()));
    }

    @PostMapping("/detail")
    public ResponseEntity<?> detail(@Valid @RequestBody IdRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseUtils.success(articleService.detailForWriter(request.getId(), principal.getUser()));
    }

    /** Creates a draft, or updates one of the writer's own articles when {@code id} is given. */
    @PostMapping({"/save-draft", "/create"})
    public ResponseEntity<?> saveDraft(@Valid @RequestBody ArticleRequest request,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        String id = articleService.save(request, principal.getUser(), false);
        return ResponseUtils.success(Map.of("id", id));
    }

    @PostMapping("/send-to-editor")
    public ResponseEntity<?> sendToEditor(@Valid @RequestBody ArticleRequest request,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        String id = articleService.save(request, principal.getUser(), true);
        return ResponseUtils.success(Map.of("id", id));
    }
}

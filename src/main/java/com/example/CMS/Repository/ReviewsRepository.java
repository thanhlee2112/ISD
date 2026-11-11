package com.example.CMS.Repository;

import com.example.CMS.Model.Article;
import com.example.CMS.Model.Reviews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewsRepository extends JpaRepository<Reviews, Long> {
    List<Reviews> findByArticle(Article article);

    Optional<Reviews> findFirstByArticleOrderByReviewedAtDesc(Article article);
}

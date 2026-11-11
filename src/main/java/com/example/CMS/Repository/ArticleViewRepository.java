package com.example.CMS.Repository;

import com.example.CMS.Model.Article;
import com.example.CMS.Model.ArticleView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ArticleViewRepository extends JpaRepository<ArticleView, Long> {
    List<ArticleView> findByArticleIn(Collection<Article> articles);
}

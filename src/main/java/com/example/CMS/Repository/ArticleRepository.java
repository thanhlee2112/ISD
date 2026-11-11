package com.example.CMS.Repository;

import com.example.CMS.Common.GlobalConstants.Status;
import com.example.CMS.Model.Article;
import com.example.CMS.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByUser(User user);

    List<Article> findByStatusNot(Status status);
}

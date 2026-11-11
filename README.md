# CMS API

Backend for a small content management system. Writers draft articles, editors review them, and admins manage users. It uses Spring Boot 3.4, Spring Security with JWT, Spring Data JPA and MySQL.

## Requirements

- Java 17 or newer
- MySQL 8 or newer

Maven is included (`./mvnw`).

## Setup

```bash
cp .env.example .env
# edit .env, then:
./mvnw spring-boot:run
```

The server starts on http://localhost:8080. It reads `.env` from the directory you start it in, and real environment variables override it. Don't put quotes around the values.

You can generate the two secrets with:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -hex 32      # ID_ENCRYPTION_KEY
```

## Configuration

Required:

- `DB_URL`: JDBC URL, for example `jdbc:mysql://localhost:3306/cms`.
- `DB_USERNAME` and `DB_PASSWORD`: the database login.
- `JWT_SECRET`: signing key, at least 32 characters.
- `ID_ENCRYPTION_KEY`: 64 hex characters (32 also work). It hides raw article IDs.

Optional:

- `JWT_EXPIRATION_MS`: token lifetime. Default `86400000` (24 hours).
- `CORS_ALLOWED_ORIGINS`: comma-separated frontend URLs. Default `http://localhost:3000`.
- `PORT`: HTTP port. Default `8080`.
- `JPA_DDL_AUTO`: Hibernate schema mode. Default `update`. Use `validate` or `none` in production.
- `SECURITY_LOG_LEVEL`: Spring Security log level. Default `INFO`.

The app won't start if a required value is missing.

## Creating the first admin

There is no sign-up, and `/admin/**` needs an admin, so add the first one by hand. The role is stored as a number: 0 is WRITER, 1 is EDITOR, 2 is ADMIN. Make a BCrypt hash of the password first, for example with
`htpasswd -bnBC 10 "" 'YourPassword' | tr -d ':\n' | sed 's/$2y/$2a/'`.

```sql
INSERT INTO user (name, username, email, password, role)
VALUES ('Admin', 'admin', 'admin@example.com', '<bcrypt-hash>', 2);
```

## API

Every endpoint except `/login` expects an `Authorization: Bearer <token>` header. Responses look like `{ "status": 200, "message": "...", "data": ... }`, except `/login`. Articles are referred to by an `encryptedId`, never by their database id.

Anyone:

- `POST /login`: takes `{username, password}` and returns `{token, name}`.

Any logged-in user:

- `POST /reset-password`: takes `{password}` (8 to 72 characters) and changes your own password. Admins can add `username` to change someone else's.

Admin:

- `GET /admin/user/list`: list users.
- `POST /admin/user/detail/{id}`: get one user.
- `POST /admin/user/store`: create a user from `{name, username, email, role}`. The generated password is returned once.
- `PUT /admin/user/edit-account`: edit `{userId, name, username, email, role}`. It doesn't change the password.
- `DELETE /admin/user/delete/{id}`: delete a user.
- `GET /admin/article/list`: all articles.
- `POST /admin/article/detail/{encryptedId}`: one article, with `viewsList`.

Writer:

- `GET /writer/article/list`: your articles.
- `POST /writer/article/detail`: takes `{id}` and returns one of your articles.
- `POST /writer/article/save-draft`: saves a draft. Include `id` to update an existing one. Fields can be incomplete.
- `POST /writer/article/send-to-editor`: same body, but title, summary and content must be filled. The article becomes `PENDING`.

Editor:

- `GET /editor/article/list`: articles sent to editors (drafts are hidden).
- `POST /editor/article/detail`: takes `{id}` and returns the article with the latest review comment.
- `POST /editor/article/review`: takes `{id, article: {title, abstract, content, status}, notes, comments}`. `article.status` is 2, 3 or 4.

Article status: 0 draft, 1 pending, 2 under review, 3 published, 4 returned to the writer. A writer can only edit a draft or a returned article, and an editor can only review a pending or under-review one.

Errors: 400 bad input, 401 missing or invalid token, 403 wrong role, 404 not found, 409 conflict.

`viewsList` (`[{date: "YYYY-MM-DD", numberOfViews}]`) comes from the `article_views` table. The API only reads it, and nothing in this project writes to it. Whatever serves the published articles needs to insert rows (`article_id`, `view_date`, `number_of_views`), otherwise the charts stay empty.
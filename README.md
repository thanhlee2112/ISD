# CMS API

REST backend for a small content-management workflow. **Writers** draft articles, **editors** review
them, and **admins** manage users. Built with Spring Boot 3.4, Spring Security (stateless JWT), Spring Data JPA and MySQL.

## Requirements

- Java 17+
- A MySQL 8+ database (tables are created automatically when `JPA_DDL_AUTO=update`)

Maven is bundled (`./mvnw`).

## Setup

```bash
cp .env.example .env      # then edit .env with your real values
./mvnw spring-boot:run    # starts on http://localhost:8080
```

The app reads `.env` from the directory it is started in; real environment variables take precedence.
Values must **not** be quoted. Generate secrets with:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -hex 32      # ID_ENCRYPTION_KEY
```

### Configuration

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | yes | | JDBC URL, e.g. `jdbc:mysql://localhost:3306/cms` |
| `DB_USERNAME` / `DB_PASSWORD` | yes | | Database credentials |
| `JWT_SECRET` | yes | | JWT signing key, at least 32 characters |
| `ID_ENCRYPTION_KEY` | yes | | AES key as 64 hex chars (32 also accepted); hides raw article IDs |
| `JWT_EXPIRATION_MS` | no | `86400000` | Token lifetime (24 h) |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:3000` | Comma-separated frontend origins |
| `PORT` | no | `8080` | HTTP port |
| `JPA_DDL_AUTO` | no | `update` | Hibernate schema mode; use `validate`/`none` in production |
| `SECURITY_LOG_LEVEL` | no | `INFO` | Spring Security log level |

The app refuses to start if a required value is missing or the keys are malformed.

## Creating the first admin

There is no public sign-up, and `/admin/**` needs an admin, so insert the first one manually
(`role` is stored as an ordinal: `0`=WRITER, `1`=EDITOR, `2`=ADMIN). Generate a BCrypt hash for the password, e.g. with
`htpasswd -bnBC 10 "" 'YourPassword' | tr -d ':\n' | sed 's/$2y/$2a/'`:

```sql
INSERT INTO user (name, username, email, password, role)
VALUES ('Admin', 'admin', 'admin@example.com', '<bcrypt-hash>', 2);
```

## API

All responses (except `/login`) use the envelope `{ "status": 200, "message": "...", "data": ... }`.
Send `Authorization: Bearer <token>` on every endpoint except `/login`. Articles are referred to by an
opaque `encryptedId`, never by their database id.

| Method & path | Role | Description |
|---|---|---|
| `POST /login` | public | `{username, password}` → `{token, name}` |
| `POST /reset-password` | any user | `{password}` (8-72 chars) changes your own password. Admins may add `username` to change anyone's |
| `GET /admin/user/list` | ADMIN | List users |
| `POST /admin/user/detail/{id}` | ADMIN | Get a user |
| `POST /admin/user/store` | ADMIN | `{name, username, email, role}`; returns a one-time generated password |
| `PUT /admin/user/edit-account` | ADMIN | `{userId, name, username, email, role}` (password is never changed here) |
| `DELETE /admin/user/delete/{id}` | ADMIN | Delete a user |
| `GET /admin/article/list` | ADMIN | All articles |
| `POST /admin/article/detail/{encryptedId}` | ADMIN | One article, including `viewsList` |
| `GET /writer/article/list` | WRITER | Your own articles |
| `POST /writer/article/detail` | WRITER | `{id}` → one of your articles |
| `POST /writer/article/save-draft` | WRITER | `{id?, title, abstract/summary, content, notes}`; creates a draft, or updates yours when `id` is given. Fields may be incomplete |
| `POST /writer/article/send-to-editor` | WRITER | Same body; title, summary and content must all be filled. Article becomes `PENDING` |
| `GET /editor/article/list` | EDITOR | Articles sent to the editors (writers' drafts are hidden) |
| `POST /editor/article/detail` | EDITOR | `{id}` → article incl. the latest review comment |
| `POST /editor/article/review` | EDITOR | `{id, article: {title, abstract, content, status}, notes, comments}`; `article.status` is `2`, `3` or `4` |

Article status codes: `0` draft, `1` pending review, `2` under review, `3` published, `4` returned to the writer.
A writer can edit an article only while it is a draft or returned; an editor can review it only while it is pending or under review.

Error codes: `400` invalid input, `401` missing/invalid token, `403` wrong role, `404` not found, `409` duplicate/conflict.

**View statistics:** `viewsList` (`[{date: "YYYY-MM-DD", numberOfViews}]`) is read from the `article_views` table, which
the API only reads. Nothing in this project records views; whatever serves the published articles must insert rows
(`article_id`, `view_date`, `number_of_views`). Until then the dashboards' charts are empty.

## Frontend

The matching React app is `ISD_FE`. Set `REACT_APP_API_URL=http://localhost:8080` in its `.env` (no `/api` suffix; this backend serves
from the root) and make sure `CORS_ALLOWED_ORIGINS` here contains the frontend's origin (default `http://localhost:3000`).

## Tests

```bash
./mvnw test
```

Tests run against in-memory H2, so no MySQL is needed.

## Production notes

- Never commit `.env`. If real credentials were ever committed to this repo's history, rotate them.
- Set `CORS_ALLOWED_ORIGINS` to your deployed frontend URL(s).
- Deploying on Railway or similar: set the variables above in the dashboard (`PORT` is provided automatically).

# Deploying with Docker

Drop Project provides pre-built Docker images at
[`ghcr.io/drop-project-edu/drop-project`](https://github.com/drop-project-edu/drop-project/pkgs/container/drop-project).
Below you will find everything you need to get a fully working instance up and running.

## Prerequisites

- Docker Engine
- Docker Compose **v2** or later (`docker compose version` to check)

## Quick Start (in-memory database)

    docker run -p 8080:8080 ghcr.io/drop-project-edu/drop-project:latest

This starts Drop Project with an H2 in-memory database. All data is **lost when the
container stops**, so this mode is only useful for trying Drop Project out. For a
persistent setup, keep reading.

## Deploying with MySQL

For a production-ready deployment use Docker Compose with MySQL.

### 1. Clone the repository

```bash
git clone https://github.com/drop-project-edu/drop-project.git
cd drop-project/deploy
```

Or simply download the contents of the `deploy/` folder (`docker-compose.yml` and
`.env.example`) — those are the only two files you need.

### 2. Configure environment variables

Copy the example file and edit it:

```bash
cp .env.example .env
```

Open `.env` in your editor and change the passwords. The variables are organised into
the following groups:

#### Database Configuration

| Variable              | Description                         |
|-----------------------|-------------------------------------|
| `MYSQL_ROOT_PASSWORD` | Root password for the MySQL server  |
| `MYSQL_DATABASE`      | Name of the database (default `dp`) |
| `MYSQL_USER`          | Application database user           |
| `MYSQL_PASSWORD`      | Password for the application user   |

#### Application Configuration

| Variable                 | Description                                                                          |
|--------------------------|--------------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | Must be set to `mysql` to use MySQL                                                  |
| `DB_URL`                 | Pre-configured in `.env.example` — no changes needed unless you renamed the database |
| `DB_USERNAME`            | Database username (should match `MYSQL_USER`)                                        |
| `DB_PASSWORD`            | Database password (should match `MYSQL_PASSWORD`)                                    |

### 3. Configure Drop Project settings (optional)

If you create a `conf/` folder next to your `docker-compose.yml`, any files you put there will be automatically
available inside the container. This lets you place a `drop-project.properties` file in `conf/` to override application
defaults — without modifying the Docker image.

Some useful settings:

| Property                                    | Description                                |
|---------------------------------------------|--------------------------------------------|
| `drop-project.admin.email`                  | Email address shown in error pages         |
| `spring.servlet.multipart.max-request-size` | Maximum upload size (e.g. `50MB`)          |
| `spring.servlet.multipart.max-file-size`    | Maximum individual file size (e.g. `50MB`) |
| `drop-project.async.timeout`                | Timeout (ms) for build operations          |
| `spring.web.locale`                         | Default locale (e.g. `pt` for Portuguese)  |
| `drop-project.mcp.enabled`                  | Enable the MCP server (`true`/`false`)     |
| `drop-project.github.token`                 | Personal access token for GitHub API calls |
| `drop-project.footer.message`               | Custom message shown in the page footer    |

### 4. Start the services

From the `deploy/` folder, run:

```bash
docker compose up -d
```

The application will be available at [http://localhost:8080](http://localhost:8080).

### Mounted volumes

The `docker-compose.yml` defines two kinds of volumes:

**Folders on your machine** (bind mounts) — these are regular folders sitting next to your
`docker-compose.yml`. You can open and edit them directly:

| Folder                 | Purpose                  |
|------------------------|--------------------------|
| `./conf`               | Configuration overrides  |
| `./submissions`        | Student submission files |
| `./assignments`        | Teacher assignment files |
| `./mavenized-projects` | Processed Maven projects |
| `./logs`               | Application log files      |
| `./backups`            | Automatic database backups |

**`db-data`** (named volume) — this is managed internally by Docker to store the MySQL
database files. You won't see a `db-data` folder on your machine; Docker handles its
location automatically. Your data persists across restarts and is only removed if you
explicitly run `docker compose down -v`.

### Database backups

The Compose stack includes an automatic backup service
([`databack/mysql-backup`](https://github.com/databacker/mysql-backup)) that creates
compressed (`.gz`) database dumps on a schedule.

By default, a backup runs **every day at 03:00**. The resulting files are stored in the
`./backups/` folder next to your `docker-compose.yml`.

To change the schedule, uncomment and edit these variables in your `.env` file:

| Variable       | Default | Description                                    |
|----------------|---------|------------------------------------------------|
| `BACKUP_FREQ`  | `1440`  | How often to back up, in minutes (1440 = 24 h) |
| `BACKUP_BEGIN` | `0300`  | Time of the first backup (24 h format)         |

**Running a backup manually:**

```bash
docker compose exec mysql-backup /entrypoint dump
```

**Restoring from a backup:**

```bash
docker compose exec mysql-backup /entrypoint restore /backup/<filename>.gz
```

### Accessing phpMyAdmin

A phpMyAdmin instance is included in the Compose stack and is available at
[http://localhost:8096](http://localhost:8096). Use it for database administration and
debugging.

### Logging

Logs are written to a rolling file (`dp.log`) in the `./logs/` folder next to your
`docker-compose.yml`. Archived logs rotate daily and when they reach 50 MB.

The log directory is configurable via the `LOGGING_FILE_PATH` environment variable
(set in `.env`). It defaults to `/usr/src/app/logs` inside the container.

### Authentication

By default, Drop Project uses a simple built-in authentication system.
For GitHub OAuth or LTI/Moodle integration, see the
[Authentication and Authorization](https://github.com/drop-project-edu/drop-project/wiki/Authentication-and-Authorization)
wiki page.

## Updating

Pull the latest Drop Project image and recreate the container:

```bash
docker compose pull
docker compose up -d --no-deps drop-project
```

## Full docker-compose.yml Example

Below is the complete Compose file for reference:

```yaml
services:
  db:
    image: mysql:8.0.35
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci
    env_file: ".env"
    healthcheck:
      test: [ "CMD", "mysqladmin" ,"ping", "-h", "localhost" ]
      interval: 5s
      timeout: 5s
      retries: 10
    volumes:
      - db-data:/var/lib/mysql

  drop-project:
    image: ghcr.io/drop-project-edu/drop-project:latest
    restart: unless-stopped
    env_file: ".env"
    volumes:
      - ./conf:/usr/src/app/conf
      - ./submissions:/usr/src/app/submissions
      - ./assignments:/usr/src/app/assignments
      - ./mavenized-projects:/usr/src/app/mavenized-projects
      - ./logs:/usr/src/app/logs
    ports:
      - 8080:8080
    depends_on:
      db:
        condition: service_healthy

  phpmyadmin:
    image: phpmyadmin/phpmyadmin:5.2.1
    restart: always
    env_file: ".env"
    ports:
      - 8096:80
    depends_on:
      db:
        condition: service_healthy

  mysql-backup:
    image: databack/mysql-backup:1.3.0
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_SERVER: db
      DB_PORT: "3306"
      DB_USER: ${MYSQL_USER}
      DB_PASS: ${MYSQL_PASSWORD}
      DB_DUMP_TARGET: /backup
      DB_DUMP_FREQ: "${BACKUP_FREQ:-1440}"
      DB_DUMP_BEGIN: "${BACKUP_BEGIN:-0300}"
    volumes:
      - ./backups:/backup

volumes:
  db-data:
```

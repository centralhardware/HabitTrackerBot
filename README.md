# TrackAll

Telegram bot for tracking anything — habits, quantities and time — with per-time reminders and inline ✅/❌ check-ins.

## Features

- Multi-user: anyone can talk to the bot, each user gets their own tracks.
- A track has a name, a type (check / quantity / timer) and one or more reminder times (`HH:MM`).
- The bot sends a reminder at each time with Done / Skip inline buttons.
- `/checkin` shows all today's slots so you can mark them manually.
- Tracks can be paused (no reminders) and later resumed.
- Stats: completion rate, current streak and trends.

## Commands

| Command | Purpose |
|---|---|
| `/start` | show help |
| `/addtrack` | add a track (interactive: asks name, type, then times) |
| `/tracks` | list active tracks |
| `/removetrack` | remove a track |
| `/pause` | pause reminders for a track |
| `/resume` | resume a paused track |
| `/checkin` | today's check-ins |
| `/stats` | statistics |
| `/tz [IANA name]` | show or set your timezone, e.g. `/tz Europe/Moscow` |

## Configuration (env)

| Variable | Description |
|---|---|
| `TELEGRAM_BOT_API_TOKEN` | Telegram bot token (read by `dev.inmo.tgbotapi.AppConfig`) |
| `DATABASE_URL` | Postgres JDBC URL, e.g. `jdbc:postgresql://localhost:5432/trackall` |
| `DATABASE_USER` | DB user (optional) |
| `DATABASE_PASSWORD` | DB password (optional) |

## Run

```bash
./gradlew run
```

Docker image via Jib:
```bash
./gradlew jibDockerBuild
```

## Schema

Flyway migrations live in `src/main/resources/db/migration`. Tables:
- `tracks` — a track, owned by a Telegram user id; has `paused_at` and soft-delete `deleted_at`
- `track_reminders` — one or more reminder times per track
- `track_params` — quantity fields of a track
- `checkins` — recorded check-ins per (track, date, reminder)
- `user_settings` — per-user settings; `timezone` is required before adding tracks

Each user must set their timezone with `/tz Europe/Moscow` before adding tracks. Reminders fire only for users with a timezone set.

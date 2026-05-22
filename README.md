# HabitTrackerBot

Telegram bot for tracking daily habits with per-time reminders and inline ✅/❌ check-ins.

## Features

- Multi-user: anyone can talk to the bot, each user gets their own habits.
- A habit has a name and one or more reminder times (`HH:MM`).
- The bot sends a reminder at each time with Done / Skip inline buttons.
- `/checkin` shows all today's slots so you can mark them manually.
- Habits can be paused (no reminders) and later resumed.
- Stats: completion rate and current streak.

## Commands

| Command | Purpose |
|---|---|
| `/start` | show help |
| `/addhabit` | add a habit (interactive: asks name, then times) |
| `/habits` | list active habits |
| `/removehabit` | remove a habit |
| `/pause` | pause reminders for a habit |
| `/resume` | resume a paused habit |
| `/checkin` | today's check-ins |
| `/stats` | statistics |
| `/tz [IANA name]` | show or set your timezone, e.g. `/tz Europe/Moscow` |

## Configuration (env)

| Variable | Description |
|---|---|
| `TELEGRAM_BOT_API_TOKEN` | Telegram bot token (read by `dev.inmo.tgbotapi.AppConfig`) |
| `DATABASE_URL` | Postgres JDBC URL, e.g. `jdbc:postgresql://localhost:5432/habits` |
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
- `habits` — habit, owned by a Telegram user id; has `paused_at` and soft-delete `deleted_at`
- `habit_reminders` — one or more reminder times (TIME) per habit
- `checkins` — `done`/`skip` per (habit, date, reminder_time)
- `user_settings` — per-user settings; `timezone` is required before adding habits

Each user must set their timezone with `/tz Europe/Moscow` before adding habits. Reminders fire only for users with a timezone set.

-- Скрипт автоматически выполняется при первом запуске контейнера PostgreSQL.
-- Создаёт две базы данных для бизнес-сервисов (database-per-service pattern).

CREATE DATABASE "stats-db";
CREATE DATABASE "ewm-db";

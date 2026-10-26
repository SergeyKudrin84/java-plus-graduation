-- Скрипт автоматически выполняется при первом запуске контейнера PostgreSQL.
-- Создаёт три базы данных для бизнес-сервисов (database-per-service pattern).

CREATE DATABASE stats_db;
CREATE DATABASE ewm_db;
CREATE DATABASE user_db;

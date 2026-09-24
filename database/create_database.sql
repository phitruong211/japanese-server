-- Run this file once with a MySQL administrator account.
-- Spring Boot/Flyway will create the application tables on the next start.

CREATE DATABASE IF NOT EXISTS japanese_learning
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'japanese_app'@'localhost'
    IDENTIFIED BY 'n3_dev_password';

GRANT ALL PRIVILEGES ON japanese_learning.*
    TO 'japanese_app'@'localhost';

FLUSH PRIVILEGES;

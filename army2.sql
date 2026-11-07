-- Army II server. Import trên MariaDB.
-- mysql -uroot -p < army2.sql
-- Tài khoản game nằm trong bảng player. Mật khẩu là chuỗi client gửi, không hash.

CREATE DATABASE IF NOT EXISTS army2
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'army2'@'localhost' IDENTIFIED BY 'army2';
GRANT ALL PRIVILEGES ON army2.* TO 'army2'@'localhost';
FLUSH PRIVILEGES;

USE army2;

CREATE TABLE IF NOT EXISTS player (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(32) NOT NULL,
  pass VARCHAR(64) NOT NULL,
  xu INT NOT NULL DEFAULT 0,
  luong INT NOT NULL DEFAULT 0,
  gun TINYINT NOT NULL DEFAULT 0,
  level INT NOT NULL DEFAULT 0,
  level_percent INT NOT NULL DEFAULT 0,
  exp INT NOT NULL DEFAULT 0,
  next_exp INT NOT NULL DEFAULT 0,
  cup INT NOT NULL DEFAULT 0,
  point INT NOT NULL DEFAULT 0,
  locked TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- locked = 1: "Tài khoản của bạn đã bị khoá, vui lòng liên hệ admin"
INSERT INTO player (id, name, pass, xu, luong, gun, level, level_percent, exp, next_exp, cup, point, locked)
VALUES
  (1, 'tester', 'secret', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
  (2, '1', '1', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  pass = VALUES(pass),
  xu = VALUES(xu),
  luong = VALUES(luong),
  gun = VALUES(gun),
  level = VALUES(level),
  level_percent = VALUES(level_percent),
  exp = VALUES(exp),
  next_exp = VALUES(next_exp),
  cup = VALUES(cup),
  point = VALUES(point),
  locked = VALUES(locked);

ALTER TABLE player AUTO_INCREMENT = 3;

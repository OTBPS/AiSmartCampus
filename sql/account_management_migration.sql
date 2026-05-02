USE smart_campus_navigation;

ALTER TABLE app_user
  MODIFY username VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

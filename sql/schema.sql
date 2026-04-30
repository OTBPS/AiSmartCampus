CREATE DATABASE IF NOT EXISTS smart_campus_navigation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_campus_navigation;

DROP TABLE IF EXISTS ai_message;
DROP TABLE IF EXISTS discover_post;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS poi;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(120) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  role VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE poi (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  category VARCHAR(40) NOT NULL,
  longitude DECIMAL(10, 6) NOT NULL,
  latitude DECIMAL(10, 6) NOT NULL,
  location_text VARCHAR(255) NOT NULL,
  open_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  tags VARCHAR(255) NOT NULL DEFAULT '',
  sheltered TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(500) DEFAULT '',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  poi_id BIGINT NULL,
  type VARCHAR(40) NOT NULL,
  content VARCHAR(600) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(600) DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL
);

CREATE TABLE discover_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  poi_id BIGINT NULL,
  category VARCHAR(40) NOT NULL,
  cover_url VARCHAR(500) DEFAULT '',
  status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question VARCHAR(600) NOT NULL,
  intent VARCHAR(50) NOT NULL,
  reply VARCHAR(1000) NOT NULL,
  tool_calls_json TEXT NOT NULL,
  map_actions_json TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_user (username, password_hash, display_name, role, status) VALUES
('student', '$2a$10$eI5SckucFgBQ2licTX9bx.oOVlLvzqARVEacwAU3VHjvIsabigadG', '学生用户', 'USER', 'ACTIVE'),
('admin', '$2a$10$eI5SckucFgBQ2licTX9bx.oOVlLvzqARVEacwAU3VHjvIsabigadG', '系统管理员', 'ADMIN', 'ACTIVE');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled) VALUES
('图书馆三楼自习区', 'STUDY', 113.934500, 22.533100, '图书馆三楼东侧，靠窗区域', 'OPEN', '安静,有插座,自习,遮蔽', 1, '适合长时间学习，晚间人较多', 1),
('第一食堂', 'DINING', 113.933700, 22.532300, '教学区南侧主干道旁', 'OPEN', '食堂,早餐,午餐,遮蔽', 1, '高峰期排队较长', 1),
('二号教学楼', 'TEACHING', 113.935200, 22.532800, '中心广场东侧', 'OPEN', '上课,教室,遮蔽', 1, '多媒体教室集中', 1),
('宿舍 A 区', 'DORM', 113.932900, 22.534000, '校园西北侧', 'OPEN', '宿舍,生活区', 0, '夜间出入需刷卡', 1),
('校园打印店', 'SERVICE', 113.934100, 22.532650, '第一食堂旁商业点', 'OPEN', '打印,复印,服务', 1, '支持彩印和装订', 1),
('校医院', 'SERVICE', 113.935800, 22.533650, '运动场北侧', 'OPEN', '医疗,健康,应急', 1, '工作日白天开放', 1);

INSERT INTO feedback (user_id, poi_id, type, content, status) VALUES
(1, 5, 'INFO_ERROR', '打印店周末下午也营业，开放信息需要补充。', 'PENDING');

INSERT INTO discover_post (title, summary, poi_id, category, cover_url, status) VALUES
('安静自习点：图书馆三楼东侧', '靠窗、有插座、距离楼梯较远，适合下午连续自习。', 1, 'STUDY', '', 'PUBLISHED'),
('雨天少淋雨：宿舍 A 区到二号教学楼', '优先经过第一食堂连廊，露天路段更短。', 3, 'ROUTE', '', 'PUBLISHED'),
('打印店位置指南', '第一食堂旁的打印店支持装订，课前高峰建议提前 20 分钟。', 5, 'SERVICE', '', 'PUBLISHED');

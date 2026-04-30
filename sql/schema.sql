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
('图书馆三楼自习区', 'STUDY', 113.934500, 22.533100, '图书馆三楼东侧，靠窗区域', 'OPEN', '安静,有插座,自习,遮蔽,夜间可达', 1, '适合长时间学习，晚间人较多', 1),
('北区学习共享空间', 'STUDY', 113.933350, 22.534180, '宿舍 A 区南侧连廊二层', 'OPEN', '安静,有插座,自习,雨天友好,夜间可达', 1, '距离宿舍近，适合晚间小组学习', 1),
('第一食堂', 'DINING', 113.933700, 22.532300, '教学区南侧主干道旁', 'OPEN', '食堂,早餐,午餐,就餐,遮蔽', 1, '高峰期排队较长', 1),
('清真餐厅', 'DINING', 113.934020, 22.532150, '第一食堂东侧一楼', 'OPEN', '食堂,就餐,清真,遮蔽', 1, '午餐高峰建议提前到达', 1),
('二号教学楼', 'TEACHING', 113.935200, 22.532800, '中心广场东侧', 'OPEN', '上课,教室,教学楼,遮蔽', 1, '多媒体教室集中', 1),
('实验楼 B 座', 'TEACHING', 113.935520, 22.533000, '二号教学楼北侧', 'OPEN', '实验,教学楼,机房,夜间可达', 0, '晚间课程需从东门进入', 1),
('宿舍 A 区', 'DORM', 113.932900, 22.534000, '校园西北侧', 'OPEN', '宿舍,生活区,夜间可达', 0, '夜间出入需刷卡', 1),
('宿舍 C 区', 'DORM', 113.932650, 22.532980, '生活区南侧', 'OPEN', '宿舍,生活区,食堂近', 0, '距离第一食堂较近', 1),
('校园打印店', 'SERVICE', 113.934100, 22.532650, '第一食堂旁商业点', 'OPEN', '打印,复印,服务,遮蔽', 1, '支持彩印和装订', 1),
('行政服务中心', 'SERVICE', 113.934760, 22.533520, '图书馆西侧一楼', 'OPEN', '办事,服务中心,证明,遮蔽', 1, '学生证明和校园卡业务集中办理', 1),
('校医院', 'SERVICE', 113.935800, 22.533650, '运动场北侧', 'OPEN', '医疗,健康,应急,雨天友好', 1, '工作日白天开放', 1),
('南门快递服务点', 'SERVICE', 113.934920, 22.531780, '南门内侧生活服务区', 'OPEN', '快递,服务,取件,夜间可达', 0, '晚间取件人流较多', 1),
('体育馆', 'SERVICE', 113.936120, 22.532420, '运动场东侧', 'OPEN', '运动,体育,夜间可达', 0, '晚间开放到 21:30', 1);

INSERT INTO feedback (user_id, poi_id, type, content, status) VALUES
(1, 9, 'INFO_ERROR', '打印店周末下午也营业，开放信息需要补充。', 'PENDING');

INSERT INTO discover_post (title, summary, poi_id, category, cover_url, status) VALUES
('安静自习点：图书馆三楼东侧', '靠窗、有插座、距离楼梯较远，适合下午连续自习。', 1, 'STUDY', '', 'PUBLISHED'),
('雨天少淋雨：宿舍 A 区到二号教学楼', '优先经过第一食堂连廊，露天路段更短。', 5, 'ROUTE', '', 'PUBLISHED'),
('打印店位置指南', '第一食堂旁的打印店支持装订，课前高峰建议提前 20 分钟。', 9, 'SERVICE', '', 'PUBLISHED'),
('晚间自习路线：宿舍到北区学习共享空间', '从宿舍 A 区沿连廊到共享空间，夜间照明更稳定。', 2, 'STUDY', '', 'PUBLISHED'),
('校医院应急位置说明', '校医院位于运动场北侧，工作日白天开放，适合作为健康服务点查询示例。', 11, 'SERVICE', '', 'PUBLISHED');

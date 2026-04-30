USE smart_campus_navigation;

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '北区学习共享空间', 'STUDY', 113.933350, 22.534180, '宿舍 A 区南侧连廊二层', 'OPEN', '安静,有插座,自习,雨天友好,夜间可达', 1, '距离宿舍近，适合晚间小组学习', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '北区学习共享空间');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '清真餐厅', 'DINING', 113.934020, 22.532150, '第一食堂东侧一楼', 'OPEN', '食堂,就餐,清真,遮蔽', 1, '午餐高峰建议提前到达', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '清真餐厅');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '实验楼 B 座', 'TEACHING', 113.935520, 22.533000, '二号教学楼北侧', 'OPEN', '实验,教学楼,机房,夜间可达', 0, '晚间课程需从东门进入', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '实验楼 B 座');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '宿舍 C 区', 'DORM', 113.932650, 22.532980, '生活区南侧', 'OPEN', '宿舍,生活区,食堂近', 0, '距离第一食堂较近', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '宿舍 C 区');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '行政服务中心', 'SERVICE', 113.934760, 22.533520, '图书馆西侧一楼', 'OPEN', '办事,服务中心,证明,遮蔽', 1, '学生证明和校园卡业务集中办理', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '行政服务中心');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '南门快递服务点', 'SERVICE', 113.934920, 22.531780, '南门内侧生活服务区', 'OPEN', '快递,服务,取件,夜间可达', 0, '晚间取件人流较多', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '南门快递服务点');

INSERT INTO poi (name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled)
SELECT '体育馆', 'SERVICE', 113.936120, 22.532420, '运动场东侧', 'OPEN', '运动,体育,夜间可达', 0, '晚间开放到 21:30', 1
WHERE NOT EXISTS (SELECT 1 FROM poi WHERE name = '体育馆');

INSERT INTO discover_post (title, summary, poi_id, category, cover_url, status)
SELECT '晚间自习路线：宿舍到北区学习共享空间', '从宿舍 A 区沿连廊到共享空间，夜间照明更稳定。', p.id, 'STUDY', '', 'PUBLISHED'
FROM poi p
WHERE p.name = '北区学习共享空间'
  AND NOT EXISTS (SELECT 1 FROM discover_post WHERE title = '晚间自习路线：宿舍到北区学习共享空间');

INSERT INTO discover_post (title, summary, poi_id, category, cover_url, status)
SELECT '校医院应急位置说明', '校医院位于运动场北侧，工作日白天开放，适合作为健康服务点查询示例。', p.id, 'SERVICE', '', 'PUBLISHED'
FROM poi p
WHERE p.name = '校医院'
  AND NOT EXISTS (SELECT 1 FROM discover_post WHERE title = '校医院应急位置说明');

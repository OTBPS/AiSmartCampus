CREATE DATABASE IF NOT EXISTS smart_campus_navigation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_campus_navigation;

DROP TABLE IF EXISTS ai_message;
DROP TABLE IF EXISTS discover_favorite;
DROP TABLE IF EXISTS discover_like;
DROP TABLE IF EXISTS discover_comment;
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
  longitude DECIMAL(18, 15) NOT NULL,
  latitude DECIMAL(18, 15) NOT NULL,
  location_text VARCHAR(255) NOT NULL,
  open_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  tags VARCHAR(255) NOT NULL DEFAULT '',
  sheltered TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(500) DEFAULT '',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  map_rank INT NULL,
  source_url VARCHAR(500) NOT NULL DEFAULT '',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_poi_map_rank (map_rank),
  CHECK (map_rank IS NULL OR (map_rank BETWEEN 1 AND 20))
);

CREATE TABLE feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  poi_id BIGINT NOT NULL,
  type VARCHAR(40) NOT NULL,
  content VARCHAR(600) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(600) DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL
);

CREATE TABLE discover_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  body TEXT NOT NULL,
  poi_id BIGINT NOT NULL,
  category VARCHAR(40) NOT NULL,
  cover_url VARCHAR(500) DEFAULT '',
  status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
  rating INT NOT NULL DEFAULT 4,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_discover_post_user (user_id),
  INDEX idx_discover_post_poi (poi_id),
  INDEX idx_discover_post_status_created (status, created_at)
);

CREATE TABLE discover_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(600) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_discover_comment_post (post_id),
  INDEX idx_discover_comment_user (user_id)
);

CREATE TABLE discover_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_discover_like_user_post (user_id, post_id),
  INDEX idx_discover_like_post (post_id)
);

CREATE TABLE discover_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_discover_favorite_user_post (user_id, post_id),
  INDEX idx_discover_favorite_post (post_id)
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
('student', '$2a$10$eI5SckucFgBQ2licTX9bx.oOVlLvzqARVEacwAU3VHjvIsabigadG', 'Student User', 'USER', 'ACTIVE'),
('admin', '$2a$10$eI5SckucFgBQ2licTX9bx.oOVlLvzqARVEacwAU3VHjvIsabigadG', 'System Admin', 'ADMIN', 'ACTIVE');

INSERT INTO poi (id, name, category, longitude, latitude, location_text, open_status, tags, sheltered, remark, enabled, map_rank, source_url, updated_at) VALUES
(1, 'NUIST Library Study Area', 'STUDY', 118.713437644448632, 32.203046572396190, 'NUIST Library facility represented as the core campus study POI', 'OPEN', 'library,quiet,outlets,study,sheltered,night-access,top20', 1, 'Official library facility used as the main study-place recommendation target.', 1, 1, 'https://en.nuist.edu.cn/4061/list.psp', '2026-05-01 20:27:31'),
(2, 'Mingde Building', 'TEACHING', 118.718282617064176, 32.204800048491641, 'Main teaching zone of the NUIST Nanjing campus', 'OPEN', 'teaching,classroom,course,sheltered,mingde,top20', 1, 'Common classroom destination for course-route demonstrations.', 1, 2, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(3, 'Wende Building', 'TEACHING', 118.721002618846285, 32.203712607025935, 'Teaching zone east of the central campus corridor', 'OPEN', 'teaching,classroom,course,wende,top20', 0, 'Alternate teaching-building search result for daily course navigation.', 1, 3, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(4, 'Shangxian Building', 'TEACHING', 118.718458307255588, 32.204216706544692, 'Teaching and academic activity building near the campus core', 'OPEN', 'teaching,classroom,academic,shangxian,top20', 1, 'High-frequency academic building used for class and meeting navigation.', 1, 4, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(5, 'Binjiang Building', 'TEACHING', 118.708523039104321, 32.199564579567053, 'Academic building in the central-west teaching area', 'OPEN', 'teaching,classroom,academic,binjiang,top20', 1, 'Academic landmark included for teaching-area search and route context.', 1, 5, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(6, 'Xiyuan Dormitory Area', 'DORM', 118.707208702821887, 32.203067752237637, 'West Garden dormitory vicinity on the NUIST Nanjing campus', 'OPEN', 'dorm,dormitory,living-area,night-access,west-garden,xiyuan,top20', 0, 'Good origin point for dormitory-to-library route demonstrations.', 1, 6, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(7, 'Zhongyuan Dormitory Area', 'DORM', 118.713979995370053, 32.200434636669570, 'Central campus student living area', 'OPEN', 'dorm,dormitory,living-area,central-campus,zhongyuan,top20', 0, 'Central living-area origin for routes to canteens and teaching buildings.', 1, 7, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(8, 'Dongyuan Dormitory Area', 'DORM', 118.719749822893874, 32.206457268884833, 'East Garden dormitory vicinity on the NUIST Nanjing campus', 'OPEN', 'dorm,dormitory,living-area,east-garden,dongyuan,top20', 0, 'Useful for east-side living area route examples.', 1, 8, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(9, 'Zhongyuan Old Canteen', 'DINING', 118.713979995370053, 32.200434636669570, 'Central campus dining area near student activity routes', 'OPEN', 'canteen,dining,breakfast,lunch,sheltered,central-campus,top20', 1, 'Reliable dining landmark for route and lunch queries.', 1, 9, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(10, 'Central Campus New Canteen', 'DINING', 118.713979995370053, 32.200434636669570, 'Central campus dining facility near the main student corridor', 'OPEN', 'canteen,dining,lunch,dinner,central-campus,sheltered,top20', 1, 'Main central dining POI for food and daily-life navigation.', 1, 10, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(11, 'Xiyuan New Canteen', 'DINING', 118.705710577833614, 32.203378137966723, 'West Garden dining area near Longshan North Road', 'OPEN', 'canteen,dining,dinner,west-garden,sheltered,top20', 1, 'West-side dining landmark for dormitory and daily-life navigation demos.', 1, 11, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(12, 'Eastern Campus Canteen', 'DINING', 118.719749822893874, 32.206457268884833, 'East-side campus dining area serving nearby dormitories and teaching areas', 'OPEN', 'canteen,dining,east-garden,dormitory-near,top20', 1, 'East-side dining POI for living-area and class-route questions.', 1, 12, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(13, 'Campus Clinic / School Hospital', 'SERVICE', 118.718137313453994, 32.203476342071426, 'School hospital service point described by NUIST Medical Service information', 'OPEN', 'clinic,medical,health,emergency,rain-friendly,top20', 1, 'Health-service POI for medical-service and emergency-help queries.', 1, 13, 'https://en.nuist.edu.cn/_s104/4217/list.psp', '2026-05-01 20:27:31'),
(14, 'Student Affairs Service Center', 'SERVICE', 118.719474472142920, 32.203397954000657, 'Student service area near the central campus axis', 'OPEN', 'service,student-affairs,campus-card,certificate,sheltered,top20', 1, 'For student affairs, certificates, and campus-card related navigation.', 1, 14, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:36:06'),
(15, 'Campus Print Shop', 'SERVICE', 118.715867577865197, 32.200852993540991, 'Campus service point near the central canteen area', 'OPEN', 'print,copy,binding,service,sheltered,top20', 1, 'Supports printing, copying, and binding related navigation scenarios.', 1, 15, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(16, 'South Gate Express Service Station / Post Office', 'SERVICE', 118.716142684934667, 32.200944906438984, 'Mail and parcel service area near the south gate', 'OPEN', 'express,parcel,pickup,post,service,top20', 0, 'Parcel and mail-service POI for daily-life navigation demos.', 1, 16, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(17, 'Agricultural Bank / Central Campus Banking Service', 'SERVICE', 118.720666503016190, 32.204394933952649, 'Central campus banking service and ATM landmark', 'OPEN', 'bank,atm,service,finance,central-campus,top20', 1, 'Banking landmark for service-search and campus-life examples.', 1, 17, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(18, 'Fengyun Theatre', 'LANDMARK', 118.717174475032763, 32.203009751250406, 'Campus cultural venue on the NUIST Nanjing campus', 'OPEN', 'theatre,event,campus-life,landmark,sheltered,fengyun,top20', 1, 'Official campus facility and event landmark.', 1, 18, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:36:06'),
(19, 'NUIST Gymnasium', 'SPORTS', 118.724662385440524, 32.206402033883819, 'NUIST gymnasium and stadium facility represented in the campus POI set', 'OPEN', 'sports,gym,basketball,fitness,night-access,top20', 0, 'Sports destination based on NUIST gymnasium and stadium information.', 1, 19, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(20, 'West Garden Observation Field', 'LANDMARK', 118.709868697894848, 32.204650917227099, 'Meteorological observation field near West Garden', 'OPEN', 'meteorology,observation,teaching,field,west-garden,top20', 0, 'NUIST-specific meteorology landmark for campus identity.', 1, 20, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(21, 'Library North Reading Room', 'STUDY', 118.713437644448632, 32.203046572396190, 'North-side reading area in the library facility', 'OPEN', 'library,reading,quiet,study,outlets', 1, 'Study-space POI under the library facility; exact seat availability is not asserted.', 1, NULL, 'https://en.nuist.edu.cn/4061/list.psp', '2026-05-01 20:27:31'),
(22, 'Library South Reading Room', 'STUDY', 118.713437644448632, 32.203046572396190, 'South-side reading area in the library facility', 'OPEN', 'library,reading,quiet,study', 1, 'Library reading POI for quiet-study search variations.', 1, NULL, 'https://en.nuist.edu.cn/4061/list.psp', '2026-05-01 20:27:31'),
(23, 'Library Digital Learning Space', 'STUDY', 118.713437644448632, 32.203046572396190, 'Digital learning space associated with the library facility', 'OPEN', 'library,digital,study,computer,learning', 1, 'Learning-space POI for computer and digital resource questions.', 1, NULL, 'https://en.nuist.edu.cn/4061/list.psp', '2026-05-01 20:27:31'),
(24, 'Library Group Study Room', 'STUDY', 118.713437644448632, 32.203046572396190, 'Group-study area associated with the library facility', 'OPEN', 'library,group-study,discussion,study', 1, 'Used for group-study search without claiming live room availability.', 1, NULL, 'https://en.nuist.edu.cn/4061/list.psp', '2026-05-01 20:27:31'),
(25, 'Xiyuan Study Lounge', 'STUDY', 118.707896850789325, 32.205647209226534, 'West Garden living area, near Xiyuan dormitories', 'OPEN', 'quiet,outlets,study,rain-friendly,night-access,west-garden', 1, 'Dorm-near study option useful for evening study recommendations.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(26, 'Zhongyuan Study Lounge', 'STUDY', 118.713979995370053, 32.200434636669570, 'Central living-area study space near Zhongyuan dormitories', 'OPEN', 'quiet,study,central-campus,dormitory-near', 1, 'Central study POI for students living near Zhongyuan.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(27, 'Dongyuan Study Lounge', 'STUDY', 118.719749822893874, 32.206457268884833, 'East Garden living-area study space near Dongyuan dormitories', 'OPEN', 'quiet,study,east-garden,dormitory-near', 1, 'East-side study POI for dormitory-near recommendations.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(28, 'Graduate Study Room', 'STUDY', 118.713437644448632, 32.203046572396190, 'Graduate student study area in the central-west campus zone', 'OPEN', 'graduate,study,quiet,academic', 1, 'Study POI for graduate-study search scenarios.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(29, 'Innovation Study Space', 'STUDY', 118.723952004968169, 32.207419628121571, 'Academic innovation and study area near the teaching zone', 'OPEN', 'innovation,study,discussion,academic', 1, 'Flexible study and discussion POI for recommendation demos.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(30, 'School of Artificial Intelligence', 'TEACHING', 118.725395685336522, 32.205598043597234, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,artificial-intelligence,teaching,research,academic', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(31, 'School of Chemistry and Materials Science', 'TEACHING', 118.725640161557934, 32.205838600348109, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,chemistry,materials,teaching,research', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(32, 'School of Atmospheric Science', 'TEACHING', 118.723638578278326, 32.205256633181179, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,atmospheric-science,meteorology,teaching,research', 1, 'Meteorology-related academic unit aligned with NUIST campus identity.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(33, 'School of Atmospheric Physics', 'TEACHING', 118.723332973863350, 32.205096089132304, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,atmospheric-physics,meteorology,teaching,research', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(34, 'School of Applied Meteorology', 'TEACHING', 118.723035043216157, 32.205336039674847, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,applied-meteorology,meteorology,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(35, 'School of Hydrology and Water Resources', 'TEACHING', 118.722752348263100, 32.205133560717790, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,hydrology,water-resources,teaching,research', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(36, 'School of Geographic Sciences', 'TEACHING', 118.722492613750831, 32.205396435841905, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,geography,geographic-sciences,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(37, 'School of Remote Sensing and Geomatics Engineering', 'TEACHING', 118.722194635087945, 32.205213005109982, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,remote-sensing,geomatics,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(38, 'School of Art', 'TEACHING', 118.725067119072420, 32.204758567602013, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,art,teaching,studio', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(39, 'School of Automation', 'TEACHING', 118.726243610630249, 32.205438753966810, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,automation,engineering,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(40, 'School of Electronic and Information Engineering', 'TEACHING', 118.726518629274750, 32.205618309250710, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,electronic-information,engineering,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(41, 'School of Environmental Science and Engineering', 'TEACHING', 118.725976189307630, 32.204839644979408, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,environmental-science,engineering,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(42, 'School of Computer Science', 'TEACHING', 118.726824206111175, 32.205858920703342, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,computer-science,software,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(43, 'School of Mathematics and Statistics', 'TEACHING', 118.725815883782460, 32.206079081881882, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,mathematics,statistics,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(44, 'School of Physics and Optoelectronic Engineering', 'TEACHING', 118.726266592763153, 32.206140587104386, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,physics,optoelectronic,engineering,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(45, 'School of Business', 'TEACHING', 118.724715788356349, 32.205616371849054, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,business,management,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(46, 'School of Management Science and Engineering', 'TEACHING', 118.724440788964301, 32.205837289743492, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,management-science,engineering,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(47, 'School of Law and Public Affairs', 'TEACHING', 118.724196287342167, 32.205436528356309, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,law,public-affairs,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(48, 'School of Liberal Arts', 'TEACHING', 118.723936517474172, 32.205176872591238, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,liberal-arts,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(49, 'School of Marine Science', 'TEACHING', 118.723195429524310, 32.204794611385566, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,marine-science,teaching,research', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(50, 'School of Marxism', 'TEACHING', 118.723737840338970, 32.204676991021628, 'School and department destination listed in NUIST campus information', 'OPEN', 'school,marxism,teaching', 1, 'Academic unit POI based on NUIST school listings.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 19:18:33'),
(51, 'Xiyuan Dormitory Building 1', 'DORM', 118.707957836019304, 32.203900411659127, 'Residential building in the Xiyuan dormitory area', 'OPEN', 'dorm,dormitory,xiyuan,west-garden,living-area', 0, 'Dormitory-area POI for west-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(52, 'Xiyuan Dormitory Building 2', 'DORM', 118.707208702821887, 32.203067752237637, 'Residential building in the Xiyuan dormitory area', 'OPEN', 'dorm,dormitory,xiyuan,west-garden,living-area', 0, 'Dormitory-area POI for west-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(53, 'Xiyuan Dormitory Building 3', 'DORM', 118.706268513624650, 32.202768761297740, 'Residential building in the Xiyuan dormitory area', 'OPEN', 'dorm,dormitory,xiyuan,west-garden,living-area', 0, 'Dormitory-area POI for west-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(54, 'Xiyuan Dormitory Building 4', 'DORM', 118.707208702821887, 32.203067752237637, 'Residential building in the Xiyuan dormitory area', 'OPEN', 'dorm,dormitory,xiyuan,west-garden,living-area', 0, 'Dormitory-area POI for west-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(55, 'Xiyuan Dormitory Building 5', 'DORM', 118.707292762908054, 32.202869547315139, 'Residential building in the Xiyuan dormitory area', 'OPEN', 'dorm,dormitory,xiyuan,west-garden,living-area', 0, 'Dormitory-area POI for west-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(56, 'Zhongyuan Dormitory Building 1', 'DORM', 118.713979995370053, 32.200434636669570, 'Residential building in the Zhongyuan dormitory area', 'OPEN', 'dorm,dormitory,zhongyuan,central-campus,living-area', 0, 'Dormitory-area POI for central-campus navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(57, 'Zhongyuan Dormitory Building 2', 'DORM', 118.713979995370053, 32.200434636669570, 'Residential building in the Zhongyuan dormitory area', 'OPEN', 'dorm,dormitory,zhongyuan,central-campus,living-area', 0, 'Dormitory-area POI for central-campus navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(58, 'Zhongyuan Dormitory Building 3', 'DORM', 118.713979995370053, 32.200434636669570, 'Residential building in the Zhongyuan dormitory area', 'OPEN', 'dorm,dormitory,zhongyuan,central-campus,living-area', 0, 'Dormitory-area POI for central-campus navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(59, 'Zhongyuan Dormitory Building 4', 'DORM', 118.713979995370053, 32.200434636669570, 'Residential building in the Zhongyuan dormitory area', 'OPEN', 'dorm,dormitory,zhongyuan,central-campus,living-area', 0, 'Dormitory-area POI for central-campus navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(60, 'Dongyuan Dormitory Building 1', 'DORM', 118.719749822893874, 32.206457268884833, 'Residential building in the Dongyuan dormitory area', 'OPEN', 'dorm,dormitory,dongyuan,east-garden,living-area', 0, 'Dormitory-area POI for east-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(61, 'Dongyuan Dormitory Building 2', 'DORM', 118.719749822893874, 32.206457268884833, 'Residential building in the Dongyuan dormitory area', 'OPEN', 'dorm,dormitory,dongyuan,east-garden,living-area', 0, 'Dormitory-area POI for east-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(62, 'Dongyuan Dormitory Building 3', 'DORM', 118.719749822893874, 32.206457268884833, 'Residential building in the Dongyuan dormitory area', 'OPEN', 'dorm,dormitory,dongyuan,east-garden,living-area', 0, 'Dormitory-area POI for east-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(63, 'Dongyuan Dormitory Building 4', 'DORM', 118.719749822893874, 32.206457268884833, 'Residential building in the Dongyuan dormitory area', 'OPEN', 'dorm,dormitory,dongyuan,east-garden,living-area', 0, 'Dormitory-area POI for east-side navigation context.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(64, 'International Student Apartment', 'DORM', 118.715516480046816, 32.205387563082169, 'Student apartment area used by international-student campus life', 'OPEN', 'dorm,apartment,international-student,living-area', 0, 'Residential POI for international student navigation context.', 1, NULL, 'https://gjy.nuist.edu.cn/english/CampusMap/list.psp', '2026-05-01 20:27:31'),
(65, 'Graduate Student Apartment', 'DORM', 118.722637909781909, 32.206865064892433, 'Graduate student apartment area in the west-central campus zone', 'OPEN', 'dorm,apartment,graduate,living-area', 0, 'Residential POI for graduate-student route examples.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 19:18:33'),
(66, 'Xiyuan Snack Street', 'DINING', 118.705710577833614, 32.203378137966723, 'Food-service street near West Garden living area', 'OPEN', 'snack,dining,west-garden,campus-life', 0, 'Food-service POI for quick dining searches near Xiyuan.', 1, NULL, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(67, 'Zhongyuan Dining Hall', 'DINING', 118.713979995370053, 32.200434636669570, 'Dining hall in the central campus dining zone', 'OPEN', 'canteen,dining,central-campus', 1, 'Central dining POI linked to the campus canteen facility category.', 1, NULL, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(68, 'Dongyuan Dining Hall', 'DINING', 118.719749822893874, 32.206457268884833, 'Dining hall near the East Garden living area', 'OPEN', 'canteen,dining,east-garden,dormitory-near', 1, 'East-side dining POI for dormitory-near recommendations.', 1, NULL, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 20:27:31'),
(69, 'Campus Cafe', 'DINING', 118.723974748826294, 32.205535448330998, 'Campus cafe listed under campus facilities information', 'OPEN', 'cafe,coffee,dining,study-break', 1, 'Cafe POI for campus-life and study-break navigation.', 1, NULL, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 19:18:33'),
(71, 'Halal Dining Counter', 'DINING', 118.724700448261714, 32.204956502271024, 'Dining-service point associated with campus canteen services', 'OPEN', 'halal,dining,canteen,service', 1, 'Dining POI for dietary-preference search scenarios.', 1, NULL, 'https://en.nuist.edu.cn/4204/list.psp', '2026-05-01 19:18:33'),
(72, 'One-Card Service Hall', 'SERVICE', 118.722882189674692, 32.204733222985638, 'Campus card service location in the central service area', 'OPEN', 'campus-card,one-card,service,student-affairs', 1, 'Campus-card service POI for student-service navigation.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(73, 'Campus Supermarket Central', 'SERVICE', 118.715562078701623, 32.202671934184998, 'Central campus supermarket and daily-supplies service point', 'OPEN', 'supermarket,shopping,service,daily-life', 1, 'Daily-supplies POI for campus-life searches.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(74, 'Xiyuan Supermarket', 'SERVICE', 118.721835638235291, 32.206173752612266, 'Supermarket service point near Xiyuan living area', 'OPEN', 'supermarket,shopping,xiyuan,west-garden,service', 1, 'West-side daily-supplies POI near dormitories.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 19:18:33'),
(75, 'Dongyuan Supermarket', 'SERVICE', 118.727481125660233, 32.205741355091568, 'Supermarket service point near Dongyuan living area', 'OPEN', 'supermarket,shopping,dongyuan,east-garden,service', 1, 'East-side daily-supplies POI near dormitories.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 19:18:33'),
(76, 'Campus Card Recharge Point', 'SERVICE', 118.722882189674692, 32.204733222985638, 'Campus card recharge service point in the central campus area', 'OPEN', 'campus-card,recharge,service,student-affairs', 1, 'Student-service POI for card recharge questions.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(77, 'Central Campus ATM', 'SERVICE', 118.720666503016190, 32.204394933952649, 'ATM service point near the central campus corridor', 'OPEN', 'atm,bank,finance,service,central-campus', 1, 'Finance-service POI for ATM searches.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(78, 'East Campus ATM', 'SERVICE', 118.720666503016190, 32.204394933952649, 'ATM service point in the east-side campus area', 'OPEN', 'atm,bank,finance,service,east-garden', 1, 'East-side finance-service POI for ATM searches.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(79, 'West Campus ATM', 'SERVICE', 118.720666503016190, 32.204394933952649, 'ATM service point in the west-side campus area', 'OPEN', 'atm,bank,finance,service,west-garden', 1, 'West-side finance-service POI for ATM searches.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(80, 'Xiyuan Laundry Room', 'SERVICE', 118.721514808541656, 32.206814151536179, 'Laundry service point near Xiyuan dormitories', 'OPEN', 'laundry,service,xiyuan,dormitory-near', 1, 'Dormitory-near daily-life service POI.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 19:18:33'),
(81, 'Dongyuan Laundry Room', 'SERVICE', 118.727740878722571, 32.206180248370046, 'Laundry service point near Dongyuan dormitories', 'OPEN', 'laundry,service,dongyuan,dormitory-near', 1, 'Dormitory-near daily-life service POI.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 19:18:33'),
(82, 'Campus Mailbox Service Point', 'SERVICE', 118.716654682580653, 32.201105790026510, 'Mailbox and mail-service point in the central service area', 'OPEN', 'mailbox,post,service,campus-life', 0, 'Mail-service POI aligned with campus service categories.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(83, 'Central Campus Parking Lot', 'SERVICE', 118.718610995622953, 32.202794216949378, 'Parking area in the central campus zone', 'OPEN', 'parking,vehicle,service,central-campus', 0, 'Parking POI for visitor and campus service navigation.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(84, 'NUIST Guest House / Visitor Hotel', 'SERVICE', 118.723378987454993, 32.206980346515515, 'Visitor accommodation service point on or near campus', 'OPEN', 'hotel,guest-house,visitor,service', 1, 'Visitor-service POI included from campus facility/service context.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 20:27:31'),
(85, 'Campus Security Office', 'SERVICE', 118.724280123533006, 32.203296870278649, 'Security and guard service point for campus safety support', 'OPEN', 'security,guard,service,safety', 1, 'Safety-service POI for emergency and visitor support searches.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 19:18:33'),
(86, 'Academic Affairs Office Service Desk', 'SERVICE', 118.723837176424183, 32.204898325608987, 'Academic affairs service desk near the central administrative area', 'OPEN', 'academic-affairs,service,certificate,course', 1, 'Academic-service POI for student administration questions.', 1, NULL, 'https://en.nuist.edu.cn/4071/listm.psp', '2026-05-01 19:18:33'),
(87, 'Main Stadium', 'SPORTS', 118.725747173232023, 32.206525267505533, 'Outdoor stadium facility in the sports area', 'OPEN', 'sports,stadium,track,field,fitness', 0, 'Sports POI based on NUIST stadium and sports-facility context.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(88, 'Central Track and Field Ground', 'SPORTS', 118.715936632789536, 32.203873908349102, 'Track and field ground in the central sports area', 'OPEN', 'sports,track,field,running', 0, 'Outdoor sports POI for running and PE-route questions.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(89, 'East Track and Field Ground', 'SPORTS', 118.725747173232023, 32.206525267505533, 'East-side outdoor track and field activity area', 'OPEN', 'sports,track,field,east-garden', 0, 'Outdoor sports POI for east-side activity searches.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(90, 'West Track and Field Ground', 'SPORTS', 118.708423850792613, 32.201444808512804, 'West-side outdoor track and field activity area', 'OPEN', 'sports,track,field,west-garden', 0, 'Outdoor sports POI for west-side activity searches.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(91, 'Central Basketball Courts', 'SPORTS', 118.720964606947121, 32.205760773020053, 'Basketball court area near the gymnasium', 'OPEN', 'sports,basketball,court,fitness', 0, 'Sports POI for basketball and activity search scenarios.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(92, 'Central Volleyball Courts', 'SPORTS', 118.715799166717787, 32.204777680730061, 'Volleyball court area near the gymnasium', 'OPEN', 'sports,volleyball,court,fitness', 0, 'Sports POI for volleyball and PE-route questions.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(93, 'Indoor Swimming Pool', 'SPORTS', 118.710869402088193, 32.199118061094310, 'Indoor swimming facility in the sports area', 'OPEN', 'sports,swimming,pool,fitness', 1, 'Sports POI included under campus sports-facility context.', 1, NULL, 'https://en.nuist.edu.cn/4062/list.psp', '2026-05-01 20:27:31'),
(94, 'Main Gate / Central Campus Gate', 'TRANSPORT', 118.726816528666390, 32.205443167301816, 'Main campus access point for visitor and route orientation', 'OPEN', 'gate,entrance,transport,visitor,main-gate', 0, 'Campus entrance POI for route starts and visitor navigation.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(95, 'South Gate', 'TRANSPORT', 118.709478296590902, 32.198089962478932, 'South-side campus entrance near service and parcel areas', 'OPEN', 'gate,entrance,transport,south-gate', 0, 'South entrance POI used for parcel and visitor routes.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(96, 'North Gate', 'TRANSPORT', 118.723952004968169, 32.207419628121571, 'North-side campus entrance and route landmark', 'OPEN', 'gate,entrance,transport,north-gate', 0, 'North entrance POI for campus boundary orientation.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(97, 'East Gate', 'TRANSPORT', 118.720307222419336, 32.202498848786995, 'East-side campus entrance near Dongyuan area', 'OPEN', 'gate,entrance,transport,east-gate', 0, 'East entrance POI for east-side arrival routes.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(98, 'Campus Bus Stop', 'TRANSPORT', 118.720307222419336, 32.202498848786995, 'Campus bus stop and public transport landmark near the south-side corridor', 'OPEN', 'bus,transport,stop,arrival', 0, 'Transport POI for arrival and departure route examples.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(99, 'Meteorological Observatory', 'LANDMARK', 118.709868697894848, 32.204650917227099, 'Meteorology-themed observation landmark on the NUIST campus', 'OPEN', 'meteorology,observatory,landmark,teaching', 0, 'Campus-identity landmark associated with NUIST meteorology strengths.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31'),
(100, 'Campus Central Square', 'LANDMARK', 118.716754326447301, 32.204393737035566, 'Central square and meeting landmark near the campus core', 'OPEN', 'square,landmark,meeting-point,central-campus', 0, 'Meeting-point POI for general campus orientation.', 1, NULL, 'https://nic.nuist.edu.cn/2473/listm.htm', '2026-05-01 20:27:31');

INSERT INTO feedback (user_id, poi_id, type, content, status) VALUES
(1, 15, 'INFO_ERROR', 'Campus Print Shop weekend service note needs administrator verification before publication.', 'PENDING');

INSERT INTO discover_post (user_id, title, summary, body, poi_id, category, cover_url, status, rating) VALUES
(1, 'Quiet Study: NUIST Library Study Area', 'Quiet library-based study POI with outlets and sheltered access tags for recommendation demos.', 'Quiet library-based study POI with outlets and sheltered access tags for recommendation demos. The library area is a reliable first stop when you need a calm place for long reading sessions.', 1, 'STUDY', '', 'PUBLISHED', 5),
(1, 'Rain-Friendly Route: Xiyuan Dormitory to Mingde Building', 'Use the dormitory, canteen, and teaching-zone POIs to demonstrate route context on rainy days.', 'Use the dormitory, canteen, and teaching-zone POIs to demonstrate route context on rainy days. The sheltered points make the route easier to explain from a campus navigation perspective.', 2, 'TEACHING', '', 'PUBLISHED', 4),
(1, 'Printing Guide: Campus Print Shop', 'Campus Print Shop is the service POI used for printing, copying, and binding queries.', 'Campus Print Shop is the service POI used for printing, copying, and binding queries. It is useful to save before exam weeks when students often need quick printing support.', 15, 'SERVICE', '', 'PUBLISHED', 4),
(1, 'Evening Study: Xiyuan Study Lounge', 'A dorm-near study option for West Garden students, without asserting live seat availability.', 'A dorm-near study option for West Garden students, without asserting live seat availability. It works well as an evening study reference when returning to the dorm area late.', 25, 'STUDY', '', 'PUBLISHED', 4),
(1, 'NUIST Landmark: West Garden Observation Field', 'A meteorology-themed campus landmark that fits NUIST navigation demonstrations.', 'A meteorology-themed campus landmark that fits NUIST navigation demonstrations. It gives the discover page a stronger campus identity beyond routine service POIs.', 20, 'LANDMARK', '', 'PUBLISHED', 5);

INSERT INTO discover_comment (post_id, user_id, content) VALUES
(1, 1, 'Library notes are especially helpful for new students looking for quiet study spots.');

INSERT INTO discover_like (post_id, user_id) VALUES
(1, 1),
(5, 1);

INSERT INTO discover_favorite (post_id, user_id) VALUES
(1, 1);

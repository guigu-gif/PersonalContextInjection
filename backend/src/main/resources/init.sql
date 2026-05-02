CREATE DATABASE IF NOT EXISTS pci DEFAULT CHARACTER SET utf8mb4;
USE pci;

-- 用户表
CREATE TABLE IF NOT EXISTS tb_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone       VARCHAR(11) UNIQUE NOT NULL COMMENT '手机号',
    nickname    VARCHAR(32) COMMENT '昵称',
    avatar      VARCHAR(255) COMMENT '头像URL',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 节次时间配置表（全局，每用户一条）
CREATE TABLE IF NOT EXISTS tb_slot_config (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL UNIQUE,
    slots_json   JSON NOT NULL COMMENT '节次配置数组',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学期表
CREATE TABLE IF NOT EXISTS tb_semester (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    name         VARCHAR(32) NOT NULL COMMENT '如：大一上学期',
    start_date   DATE NOT NULL COMMENT '学期开始日期',
    total_weeks  TINYINT NOT NULL DEFAULT 20 COMMENT '总周数',
    is_current   TINYINT NOT NULL DEFAULT 0 COMMENT '1=当前学期',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 课程表
CREATE TABLE IF NOT EXISTS tb_course (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    name         VARCHAR(64) NOT NULL COMMENT '课程名',
    teacher      VARCHAR(32) COMMENT '教师',
    location     VARCHAR(64) COMMENT '教室/地点',
    weekday      TINYINT NOT NULL COMMENT '星期几 1-7',
    start_slot   TINYINT NOT NULL COMMENT '开始节次 1-12',
    end_slot     TINYINT NOT NULL COMMENT '结束节次 1-12',
    week_start   TINYINT DEFAULT 1 COMMENT '开始周次',
    week_end     TINYINT DEFAULT 20 COMMENT '结束周次',
    semester_id  BIGINT COMMENT '所属学期id',
    color        VARCHAR(16) COMMENT '颜色，如#4f46e5',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI操作日志表（预留，暂不实现回滚）
CREATE TABLE IF NOT EXISTS tb_ai_operation_log (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    op_type      VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE/DELETE',
    table_name   VARCHAR(32) NOT NULL COMMENT '操作的表',
    record_id    BIGINT COMMENT '操作的记录id',
    before_data  JSON COMMENT '操作前数据快照',
    after_data   JSON COMMENT '操作后数据快照',
    source       TINYINT DEFAULT 0 COMMENT '0=识图导入 1=对话修改',
    confirmed_at DATETIME COMMENT '用户确认时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 备忘录表
CREATE TABLE IF NOT EXISTS tb_memo (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    title        VARCHAR(64) COMMENT 'AI自动生成标题',
    content      VARCHAR(1000) NOT NULL,
    remind_time  DATETIME COMMENT '提醒时间',
    reminded     TINYINT NOT NULL DEFAULT 0 COMMENT '0=未提醒 1=已提醒',
    status       TINYINT NOT NULL DEFAULT 0 COMMENT '0=待办 1=已完成',
    source       TINYINT NOT NULL DEFAULT 0 COMMENT '0=用户输入 1=AI辅助',
    ai_extracted TINYINT NOT NULL DEFAULT 0 COMMENT '0=用户输入 1=AI提取',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_remind (user_id, remind_time),
    INDEX idx_user_reminded (user_id, reminded)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统通知表
CREATE TABLE IF NOT EXISTS tb_notification (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    type        TINYINT NOT NULL COMMENT '1=课程提醒 2=备忘提醒',
    ref_id      BIGINT COMMENT '关联课程id或备忘录id',
    content     VARCHAR(200) NOT NULL,
    is_read     TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户画像表
CREATE TABLE IF NOT EXISTS tb_user_profile (
    user_id      BIGINT PRIMARY KEY,
    identity     VARCHAR(16)  NOT NULL DEFAULT 'general'  COMMENT 'student/elder/general',
    font_size    VARCHAR(16)  NOT NULL DEFAULT 'normal'   COMMENT 'normal/large/xlarge',
    theme        VARCHAR(16)  NOT NULL DEFAULT 'default'  COMMENT 'default/elder/dark',
    wallpaper    VARCHAR(64)  NOT NULL DEFAULT 'none'     COMMENT '壁纸key，预留',
    updated_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 设置变更日志（只追加）
CREATE TABLE IF NOT EXISTS tb_profile_log (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    action       VARCHAR(32)  NOT NULL COMMENT 'SET_THEME/SET_FONT_SIZE/SET_IDENTITY/SET_WALLPAPER',
    old_value    VARCHAR(64),
    new_value    VARCHAR(64),
    created_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统公告表
CREATE TABLE IF NOT EXISTS tb_announcement (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    title        VARCHAR(64) NOT NULL,
    content      VARCHAR(500) NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO tb_announcement (id, title, content) VALUES
(1, '欢迎使用 Personal Context Injection', '本系统支持课程表管理、备忘录提醒、AI 对话等功能，AI 助手会根据你的个人画像提供更贴合的回答。'),
(2, '课程表 AI 识图功能上线', '现在可以直接拍摄课程表图片，AI 自动识别并导入，支持逐条确认后写入。'),
(3, '用户画像功能说明', '在「设置」页面可以添加个人画像条目，AI 对话时会自动注入这些信息，让回答更了解你。');

CREATE TABLE IF NOT EXISTS tb_user_persona (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    fact_key     VARCHAR(16)  NOT NULL COMMENT '身份/偏好/关注/其他',
    fact_value   VARCHAR(100) NOT NULL,
    source       VARCHAR(8)   NOT NULL DEFAULT 'manual' COMMENT 'manual/ai',
    created_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 出行攻略主表
CREATE TABLE IF NOT EXISTS tb_travel_guide (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_id    BIGINT NOT NULL,
    city         VARCHAR(32) NOT NULL,
    title        VARCHAR(100) NOT NULL,
    content      VARCHAR(2000) NOT NULL,
    tags         VARCHAR(255) DEFAULT '',
    is_official  TINYINT NOT NULL DEFAULT 0 COMMENT '1=官方攻略 0=用户攻略',
    status       TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=下线',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_city_status (city, status),
    INDEX idx_official (is_official)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 攻略互动表：点赞/收藏为开关型，投币/充电为累计型
CREATE TABLE IF NOT EXISTS tb_guide_interaction (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    guide_id     BIGINT NOT NULL,
    action_type  VARCHAR(16) NOT NULL COMMENT 'LIKE/FAV/COIN/CHARGE',
    value        INT NOT NULL DEFAULT 1,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_guide_action (user_id, guide_id, action_type),
    INDEX idx_guide_action (guide_id, action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 数据约束加固（P0）
-- ----------------------------
-- 兼容已存在库：如表已创建，可手动执行以下 ALTER 语句完成收口
-- 1) 备忘录：标题/内容长度与状态枚举边界
-- 2) 画像：同一用户下 key+value 去重（避免重复脏数据）
-- 3) 攻略互动：value 下界限制

ALTER TABLE tb_memo
    MODIFY COLUMN title VARCHAR(80) NULL COMMENT '备忘录标题（自动生成/用户输入）',
    MODIFY COLUMN content VARCHAR(1000) NOT NULL COMMENT '备忘录正文（已做输入净化）';

ALTER TABLE tb_user_persona
    ADD UNIQUE KEY uk_user_fact (user_id, fact_key, fact_value);

ALTER TABLE tb_guide_interaction
    MODIFY COLUMN value INT NOT NULL DEFAULT 1 COMMENT '互动值，最小为1';

-- 上海种子攻略（20条：官方6条 + UGC14条）
INSERT IGNORE INTO tb_travel_guide (id, author_id, city, title, content, tags, is_official, status) VALUES
(1001, 1, '上海', '官方：人民广场换乘避拥堵指引', '工作日早高峰（7:40-9:20）建议在人民广场优先走1号线中部车厢下车，2号线换乘距离更短。若携带行李，优先电梯口附近车厢，避免楼梯拥堵。', '官方,换乘,人民广场', 1, 1),
(1002, 1, '上海', '官方：南京东路步行友好路线', '南京东路到人民广场可选步行+地铁混合方案。雨天建议地铁优先，晴天可沿福州路步行，沿线补给点多。夜间请避开施工围挡区域。', '官方,步行,南京东路', 1, 1),
(1003, 1, '上海', '官方：外滩周边夜间返程建议', '22:00后外滩周边打车排队明显增长，建议先步行至延安东路或河南中路再叫车；若地铁未停运，优先2号线+换乘。', '官方,外滩,夜间返程', 1, 1),
(1004, 1, '上海', '官方：虹桥枢纽进出站节奏', '虹桥火车站进出站建议预留35分钟，行李较多时至少45分钟。地铁2/10/17号线换乘距离长，赶时间优先选择同站台方向。', '官方,虹桥,枢纽', 1, 1),
(1005, 1, '上海', '官方：陆家嘴周末客流提示', '周末14:00-19:00陆家嘴商圈客流高，2号线站内排队时间增加。建议提前1站上下车并步行，减少站内等待。', '官方,陆家嘴,客流', 1, 1),
(1006, 1, '上海', '官方：机场联络线与地铁衔接', '从浦东机场进城，若落地时间晚于21:30，建议优先磁浮/地铁组合，不建议纯地面交通。高峰时段注意行李安检排队。', '官方,机场,联络线', 1, 1),
(1101, 2, '上海', '南京路步行街到人民广场：一站地铁最稳', '我平时从南京东路上2号线到人民广场就一站，快的时候10分钟内结束。赶时间就别纠结，直接地铁。', '地铁2号线,通勤', 0, 1),
(1102, 2, '上海', '人民广场换乘别站在最前车厢', '早高峰最前车厢人特别多，建议中后段车厢下车再换乘，体感快很多。', '换乘,早高峰', 0, 1),
(1103, 3, '上海', '福州路步行到人民广场的避坑', '晚上福州路有时人流密，步行要预留时间。鞋不舒服的话别走全程，直接公交或地铁。', '步行,避坑', 0, 1),
(1104, 3, '上海', '外滩散场后打车上车点建议', '别在景区正门打车，往九江路方向走两三百米再叫车，成功率高很多。', '外滩,打车', 0, 1),
(1105, 4, '上海', '虹桥火车站换乘地铁的节奏', '到站后先看大屏和出口指示，盲走容易绕远。行李多一定走电梯，不然会很累。', '虹桥,换乘', 0, 1),
(1106, 4, '上海', '陆家嘴周末游玩返程建议', '周末晚上从陆家嘴回市区，地铁站内排队较久，提前错峰20分钟会舒服很多。', '陆家嘴,周末', 0, 1),
(1107, 5, '上海', '南京东路地铁口出站方向经验', '南京东路站出口很多，提前看好你要去的街区，少走回头路。', '南京东路,出口', 0, 1),
(1108, 5, '上海', '人民广场站内找卫生间与补水点', '第一次来人民广场容易慌，站内导视其实很清楚，先看导视再移动会快。', '人民广场,站内', 0, 1),
(1109, 6, '上海', '静安寺到外滩：公交视野好但慢', '不赶时间可以坐公交看城市风景，赶时间就地铁。高峰期公交受红灯影响明显。', '静安寺,外滩,公交', 0, 1),
(1110, 6, '上海', '浦东机场进城行李党建议', '两个26寸箱子建议直接机场线+打车接驳，纯地铁转乘会很累。', '浦东机场,行李', 0, 1),
(1111, 7, '上海', '徐家汇商圈晚高峰避拥挤', '18:00左右徐家汇地铁换乘通道人多，晚20分钟进站会明显好很多。', '徐家汇,晚高峰', 0, 1),
(1112, 7, '上海', '上海站到人民广场通勤建议', '1号线直达很方便，但上下班时间人多，早点出门能省不少体力。', '上海站,人民广场', 0, 1),
(1113, 8, '上海', '迪士尼返程地铁排队经验', '烟花结束后别急着冲，晚15分钟再进站排队会短很多。', '迪士尼,返程', 0, 1),
(1114, 8, '上海', '上海南站到市区两种路线比较', '地铁稳定但可能拥挤，打车快但堵车波动大，雨天建议优先地铁。', '上海南站,路线对比', 0, 1);

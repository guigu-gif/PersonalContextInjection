package com.pci.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SchemaMigrationRunner implements CommandLineRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        ensureTable("tb_travel_guide",
                "CREATE TABLE tb_travel_guide (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "author_id BIGINT NOT NULL," +
                        "city VARCHAR(32) NOT NULL," +
                        "title VARCHAR(100) NOT NULL," +
                        "content VARCHAR(2000) NOT NULL," +
                        "tags VARCHAR(255) DEFAULT ''," +
                        "is_official TINYINT NOT NULL DEFAULT 0," +
                        "status TINYINT NOT NULL DEFAULT 1," +
                        "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "INDEX idx_city_status (city, status)," +
                        "INDEX idx_official (is_official)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        ensureTable("tb_guide_interaction",
                "CREATE TABLE tb_guide_interaction (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "user_id BIGINT NOT NULL," +
                        "guide_id BIGINT NOT NULL," +
                        "action_type VARCHAR(16) NOT NULL," +
                        "value INT NOT NULL DEFAULT 1," +
                        "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "UNIQUE KEY uk_user_guide_action (user_id, guide_id, action_type)," +
                        "INDEX idx_guide_action (guide_id, action_type)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        ensureColumn("tb_memo", "status",
                "ALTER TABLE tb_memo ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0=todo 1=done 2=archived' AFTER source");
        ensureColumn("tb_memo", "ai_extracted",
                "ALTER TABLE tb_memo ADD COLUMN ai_extracted TINYINT NOT NULL DEFAULT 0 COMMENT '0=manual 1=ai-assisted' AFTER status");
        ensureIndex("tb_memo", "idx_user_status",
                "ALTER TABLE tb_memo ADD INDEX idx_user_status (user_id, status)");
        ensureSeedGuideData();
    }

    private void ensureColumn(String tableName, String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void ensureIndex(String tableName, String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void ensureTable(String tableName, String createSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(createSql);
        }
    }

    private void ensureSeedGuideData() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_travel_guide",
                Integer.class
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("INSERT INTO tb_travel_guide (id, author_id, city, title, content, tags, is_official, status) VALUES " +
                "(1001,1,'上海','官方：人民广场换乘避拥堵指引','工作日早高峰建议在人民广场优先走1号线中部车厢下车，2号线换乘距离更短。','官方,换乘,人民广场',1,1)," +
                "(1002,1,'上海','官方：南京东路步行友好路线','南京东路到人民广场可选步行+地铁混合方案。雨天建议地铁优先。','官方,步行,南京东路',1,1)," +
                "(1003,1,'上海','官方：外滩周边夜间返程建议','22:00后外滩周边打车排队明显增长，建议先步行至延安东路再叫车。','官方,外滩,夜间返程',1,1)," +
                "(1004,1,'上海','官方：虹桥枢纽进出站节奏','虹桥火车站进出站建议预留35分钟，行李较多时至少45分钟。','官方,虹桥,枢纽',1,1)," +
                "(1005,1,'上海','官方：陆家嘴周末客流提示','周末14:00-19:00陆家嘴客流高，建议提前1站上下车并步行。','官方,陆家嘴,客流',1,1)," +
                "(1006,1,'上海','官方：机场联络线与地铁衔接','浦东机场进城晚于21:30建议优先磁浮/地铁组合。','官方,机场,联络线',1,1)," +
                "(1101,2,'上海','南京路步行街到人民广场：一站地铁最稳','从南京东路上2号线到人民广场就一站，赶时间直接地铁。','地铁2号线,通勤',0,1)," +
                "(1102,2,'上海','人民广场换乘别站在最前车厢','早高峰最前车厢人多，建议中后段车厢下车换乘。','换乘,早高峰',0,1)," +
                "(1103,3,'上海','福州路步行到人民广场的避坑','晚上福州路人流密，步行要预留时间。','步行,避坑',0,1)," +
                "(1104,3,'上海','外滩散场后打车上车点建议','别在景区正门打车，往九江路方向走两三百米再叫车。','外滩,打车',0,1)," +
                "(1105,4,'上海','虹桥火车站换乘地铁的节奏','到站先看大屏和出口指示，盲走容易绕远。','虹桥,换乘',0,1)," +
                "(1106,4,'上海','陆家嘴周末游玩返程建议','周末晚上从陆家嘴回市区，站内排队较久，提前错峰。','陆家嘴,周末',0,1)," +
                "(1107,5,'上海','南京东路地铁口出站方向经验','南京东路站出口多，提前看好街区方向。','南京东路,出口',0,1)," +
                "(1108,5,'上海','人民广场站内找补给点','第一次来人民广场先看导视再移动效率更高。','人民广场,站内',0,1)," +
                "(1109,6,'上海','静安寺到外滩：公交视野好但慢','不赶时间坐公交看风景，赶时间用地铁。','静安寺,外滩,公交',0,1)," +
                "(1110,6,'上海','浦东机场进城行李党建议','行李多建议机场线+打车接驳，纯地铁转乘较累。','浦东机场,行李',0,1)," +
                "(1111,7,'上海','徐家汇商圈晚高峰避拥挤','18点左右换乘通道人多，晚20分钟进站更舒适。','徐家汇,晚高峰',0,1)," +
                "(1112,7,'上海','上海站到人民广场通勤建议','1号线直达方便，但上下班高峰较拥挤。','上海站,人民广场',0,1)," +
                "(1113,8,'上海','迪士尼返程地铁排队经验','烟花结束后晚15分钟进站，排队明显更短。','迪士尼,返程',0,1)," +
                "(1114,8,'上海','上海南站到市区路线比较','地铁稳定但拥挤，打车快但堵车波动大。','上海南站,路线对比',0,1)");
    }
}

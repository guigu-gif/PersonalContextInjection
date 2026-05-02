package com.pci.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final long LOGIN_USER_TTL = 36000L; // 30分钟，秒

    public static final String MEMO_REMIND_QUEUE = "memo:remind:queue";
    public static final String COURSE_REMIND_QUEUE = "course:remind:queue";

    public static final String RECOMMEND_SESSION_KEY = "recommend:session:";
    public static final long RECOMMEND_SESSION_TTL = 30 * 60;
}

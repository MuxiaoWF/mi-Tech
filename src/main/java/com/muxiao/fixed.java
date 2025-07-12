package com.muxiao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.muxiao.tools.sendGetRequest;

class fixed {
    protected static final String SALT_6X = "t0qEgfub6cvueAPgR5m9aQWWVciEer7v";
    protected static final String SALT_4X = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs";
    protected static final String LK2 = "IDMtPWQJfBCJSLOFxOlNjiIFVasBLttg";
    protected static final String K2 = "aApXDrhCxFhZkKZQVWWyfoAlyHTlJkis";

    protected static final String Honkai2_act_id = "e202203291431091";
    protected static final String Honkai3rd_act_id = "e202306201626331";
    protected static final String HonkaiStarRail_act_id = "e202304121516551";
    protected static final String Genshin_act_id = "e202311201442471";
    protected static final String TearsOfThemis_act_id = "e202202251749321";
    protected static final String ZZZ_act_id = "e202406242138391";
    protected static final Map<String, String> game_id_to_name = new HashMap<>() {{
        put("bh2_cn", "崩坏2");
        put("bh3_cn", "崩坏3");
        put("nxx_cn", "未定事件簿");
        put("hk4e_cn", "原神");
        put("hkrpg_cn", "星铁");
        put("nap_cn", "绝区零");
    }};
    protected static final Map<String, String> genshin_TCG_headers = new HashMap<>() {{
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
        put("Accept", "application/json, text/plain, */*");
        put("Origin", "https://webstatic.mihoyo.com");
        put("Referer", "https://webstatic.mihoyo.com/");
        put("Accept-Language", "zh-CN,zh;q=0.9");
        put("Cookie", "");
    }};
    protected static final List<Map<String, String>> bbs_list = new ArrayList<>(Arrays.asList(
            new HashMap<>() {{
                put("id", "1");
                put("forumId", "1");
                put("name", "崩坏3");
            }},
            new HashMap<>() {{
                put("id", "2");
                put("forumId", "26");
                put("name", "原神");
            }}, new HashMap<>() {{
                put("id", "3");
                put("forumId", "30");
                put("name", "崩坏2");
            }}, new HashMap<>() {{
                put("id", "4");
                put("forumId", "37");
                put("name", "未定事件簿");
            }}, new HashMap<>() {{
                put("id", "5");
                put("forumId", "34");
                put("name", "大别野");
            }}, new HashMap<>() {{
                put("id", "6");
                put("forumId", "52");
                put("name", "星铁");
            }}, new HashMap<>() {{
                put("id", "8");
                put("forumId", "57");
                put("name", "绝区零");
            }}));
    protected static final String deviceId = getDeviceId(tools.files.read_global().get("device_name_space"), tools.files.read_global().get("device_name"));
    private static final Map<String, String> name_to_game_id = new HashMap<>() {{
        put("崩坏2", "bh2_cn");
        put("崩坏3", "bh3_cn");
        put("未定事件簿", "nxx_cn");
        put("原神", "hk4e_cn");
        put("星铁", "hkrpg_cn");
        put("绝区零", "nap_cn");
    }};
    private static final String bbs_version = "2.92.0";
    private static final String user_agent = "Mozilla/5.0 (Linux; Android 12; mi-Tech) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/103.0.5060.129 Mobile Safari/537.36 miHoYoBBS/" + bbs_version;
    protected static final Map<String, String> fp_headers = new HashMap<>() {{
        put("User-Agent", user_agent);
        put("x-rpc-app_version", bbs_version);
        put("x-rpc-client_type", "5");
        put("Referer", "https://webstatic.mihoyo.com/");
        put("Origin", "https://webstatic.mihoyo.com/");
        put("Content-Type", "application/json; utf-8");
        put("Accept-Language", "zh-CN,zh-Hans;q=0.9");
    }};
    protected static final Map<String, String> authkey_headers = new java.util.HashMap<>() {{
        put("Cookie", "");
        put("DS", getDS(LK2));
        put("x-rpc-app_version", bbs_version);
        put("x-rpc-client_type", "5");
        put("Content-Type", "application/json; charset=utf-8");
        put("Host", "api-takumi.mihoyo.com");
        put("Connection", "Keep-Alive");
        put("Accept-Encoding", "gzip");
        put("User-Agent", user_agent);
    }};
    protected static final Map<String, String> game_login_headers = new HashMap<>() {{
        put("Accept", "application/json; utf-8");
        put("DS", getDS(LK2));
        put("x-rpc-channel", "miyousheluodi");
        put("Origin", "https://webstatic.mihoyo.com");
        put("x-rpc-app_version", bbs_version);
        put("User-Agent", user_agent);
        put("x-rpc-client_type", "5");
        put("Referer", "");
        put("Accept-Encoding", "gzip, deflate, br");
        put("Accept-Language", "zh-CN,en-US;q=0.8");
        put("X-Requested-With", "com.mihoyo.hyperion");
        put("Cookie", "");
        put("x-rpc-device_id", deviceId);
    }};
    private static final String app_id = "bll8iq97cem8";
    protected static final Map<String, String> bbs_headers = new HashMap<>() {{
        put("DS", getDS(K2));
        put("Accept", "application/json; utf-8");
        put("Origin", "https://webstatic.mihoyo.com");
        put("x-rpc-client_type", "2");
        put("x-rpc-app_version", bbs_version);
        put("x-rpc-sys_version", "14");
        put("x-rpc-channel", "miyousheluodi");
        put("x-rpc-device_id", deviceId);
        put("x-rpc-device_name", "mi-Tech-Device");
        put("x-rpc-device_model", "mi-Tech");
        put("Referer", "https://app.mihoyo.com");
        put("Host", "bbs-api.mihoyo.com");
        put("User-Agent", user_agent);
        put("x-rpc-verify_key", app_id);
    }};
    protected static final Map<String, String> get_token_by_stoken_headers = new HashMap<>() {{
        put("Accept", "application/json; utf-8");
        put("x-rpc-channel", "miyousheluodi");
        put("Origin", "https://webstatic.mihoyo.com");
        put("x-rpc-app_version", bbs_version);
        put("User-Agent", user_agent);
        put("x-rpc-client_type", "5");
        put("Referer", "");
        put("Accept-Encoding", "gzip, deflate");
        put("Accept-Language", "zh-CN,en-US;q=0.8");
        put("X-Requested-With", "com.mihoyo.hyperion");
        put("Cookie", "");
        put("x-rpc-device_id", deviceId);
        put("x-rpc-app_id", app_id);
    }};
    protected static final Map<String, String> gameToken_headers = new HashMap<>() {{
        put("x-rpc-app_version", bbs_version);
        put("DS", "");
        put("x-rpc-aigis", "");
        put("Content-Type", "application/json");
        put("Accept", "application/json");
        put("x-rpc-game_biz", "bbs_cn");
        put("x-rpc-sys_version", "14");
        put("x-rpc-device_id", deviceId);
        put("x-rpc-device_name", "mi-Tech-Device");
        put("x-rpc-device_model", "mi-Tech");
        put("x-rpc-app_id", app_id);
        put("x-rpc-client_type", "4");
        put("User-Agent", user_agent);
    }};
    protected static String publicKeyString = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDvekdPMHN3AYhm/vktJT+YJr7cI5DcsNKqdsx5DZX0gDuWFuIjzdwButrIYPNmRJ1G8ybDIF7oDW2eEpm5sMbL9zs9ExXCdvqrn51qELbqj0XxtMTIpaCHFSI50PfPpTFV9Xt/hmyVwokoOXFlAEgCn+QCgGs52bFoYMtyi+xEQIDAQAB";
    private static String seedId;
    private static String seedTime;
    protected static Map<String, String> captcha_headers = new HashMap<>() {{
        put("x-rpc-account_version", "2.20.1");
        put("x-rpc-app_id", app_id);
        put("x-rpc-device_name", "mi-Tech-Device");
        put("x-rpc-device_fp", getFp());
        put("x-rpc-app_version", bbs_version);
        put("ds", getDS(K2));
        put("x-rpc-client_type", "2");
        put("x-rpc-device_id", deviceId);
        put("x-rpc-sdk_version", "2.20.1");
        put("x-rpc-sys_version", "14");
        put("x-rpc-game_biz", "bbs_cn");
        put("Content-Type", "application/json; utf-8");
    }};
    protected static final Map<String, String> record_headers = new HashMap<>() {{
        put("x-rpc-client_type", "5");
        put("DS", "");
        put("User-Agent", user_agent);
        put("X-Request-With", "com.mihoyo.hyperion");
        put("Origin", "https://webstatic.mihoyo.com");
        put("Referer", "https://webstatic.mihoyo.com/");
        put("x-rpc-device_fp", getFp());
        put("x-rpc-device_id", deviceId);
        put("x-rpc-app_version", bbs_version);
        put("x-rpc-device_name", "mi-Tech-Device");
        put("x-rpc-page", "v5.3.2-gr-cn_#/ys");
        put("x-rpc-tool_version", "v5.3.2-gr-cn");
        put("sec-fetch-site", "same-site");
        put("sec-fetch-mode", "cors");
        put("sec-fetch-dest", "empty");
        put("accept-encoding", "gzip, deflate, br");
        put("accept", "*/*");
        put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        put("x-rpc-sys_version", "14");
    }};
    protected static final Map<String, String> widget_headers = new HashMap<>() {{
        put("x-rpc-client_type", "2");
        put("DS", getDS(K2));
        put("x-rpc-app_version", bbs_version);
        put("x-rpc-device_id", deviceId);
        put("x-rpc-sys_version", "14");
        put("x-rpc-device_name", "mi-Tech-Device");
        put("x-rpc-device_model", "mi-Tech");
        put("x-rpc-device_fp", getFp());
        put("x-rpc-channel", "miyousheluodi");
        put("Referer", "https://app.mihoyo.com");
        put("cookie", "");
        put("x-rpc-h256_supported", "1");
        put("x-rpc-verify_key", app_id);
        put("x-rpc-csm_source", "home");
        put("User-Agent", user_agent);
        put("Connection", "Keep-Alive");
        put("Accept-Encoding", "gzip");
    }};
    protected static final Map<String, String> user_game_roles_stoken_headers = new HashMap<>() {{
        put("x-rpc-client_type", "2");
        put("Accept-Encoding", "gzip, deflate, br");
        put("x-rpc-device_id", deviceId);
        put("x-rpc-device_fp", getFp());
        put("x-rpc-verify_key", app_id);
        put("User-Agent", user_agent);
        put("x-rpc-app_version", bbs_version);
        put("DS", getDS(K2));
        put("Cookie", "");
    }};
    protected final static Map<String, String> password_headers = new HashMap<>() {{
        put("User-Agent", user_agent);
        put("x-rpc-account_version", "2.20.1");
        put("x-rpc-app_id", app_id);
        put("x-rpc-device_name", "mi-Tech-Device");
        put("x-rpc-device_fp", getFp());
        put("x-rpc-app_version", bbs_version);
        put("ds", getDS(K2));
        put("x-rpc-client_type", "2");
        put("x-rpc-device_id", deviceId);
        put("x-rpc-sdk_version", "2.20.1");
        put("x-rpc-sys_version", "14");
        put("x-rpc-game_biz", "bbs_cn");
    }};

    protected static String name_to_game_num_id(String game_name) {
        for (Map<String, String> map : bbs_list) {
            if (map.get("name").equals(game_name)) {
                return map.get("id");
            }
        }
        return null;
    }

    /**
     * 获取游戏id（game_biz），可输入崩坏2、原神、崩坏3、绝区零、星铁、绝区零
     */
    protected static String name_to_game_id(String game_name) {
        return name_to_game_id.get(game_name);
    }

    /**
     * 创建一个设备deviceID
     *
     * @param namespace 随便来个字符串URL
     * @param name      随便来个字符串（为确保唯一性最好用唯一的cookie）
     * @return deviceID -String
     */
    private static String getDeviceId(String namespace, String name) {
        if (namespace == null)
            namespace = "https://github.com/MuxiaoWF/mi-Tech";
        if (name == null)
            name = "muxiaoOwO";
        // Convert namespace to UUID
        UUID namespaceUUID = UUID.nameUUIDFromBytes(namespace.getBytes());
        // Concatenate namespace and name
        long msb = namespaceUUID.getMostSignificantBits();
        long lsb = namespaceUUID.getLeastSignificantBits();
        byte[] namespaceBytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            namespaceBytes[i] = (byte) (msb >>> (8 * (7 - i)));
        }
        for (int i = 8; i < 16; i++) {
            namespaceBytes[i] = (byte) (lsb >>> (8 * (15 - i)));
        }
        byte[] nameBytes = name.getBytes();
        byte[] combinedBytes = new byte[namespaceBytes.length + nameBytes.length];
        System.arraycopy(namespaceBytes, 0, combinedBytes, 0, namespaceBytes.length);
        System.arraycopy(nameBytes, 0, combinedBytes, namespaceBytes.length, nameBytes.length);
        // Hash the combined bytes using MD5
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hashBytes = md.digest(combinedBytes);
        // Convert the hash to a UUID
        long mostSignificantBits = 0;
        long leastSignificantBits = 0;
        for (int i = 0; i < 8; i++) {
            mostSignificantBits = (mostSignificantBits << 8) | (hashBytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            leastSignificantBits = (leastSignificantBits << 8) | (hashBytes[i] & 0xff);
        }
        // Set the version to 3 and the variant to DCE 1.1
        mostSignificantBits &= ~0x000000000000F000L;
        mostSignificantBits |= 0x0000000000003000L;
        leastSignificantBits &= ~0xC000000000000000L;
        leastSignificantBits |= 0x8000000000000000L;
        return new UUID(mostSignificantBits, leastSignificantBits).toString();
    }

    /**
     * 通过stoken获取cookie_token
     */
    protected static String getCookieTokenByStoken() {
        String stoken = tools.files.read().get("stoken");
        String stuid = tools.files.read().get("stuid");
        if (stoken == null || stoken.isEmpty() && stuid == null || stuid.isEmpty()) {
            throw new RuntimeException("Stoken和Suid为空，无法自动更新CookieToken");
        }
        String cookie = "stuid=" + stuid + ";stoken=" + stoken;
        if (stoken.startsWith("v2_")) {
            if (tools.files.read().get("mid") == null)
                throw new RuntimeException("v2的stoken获取cookie_token时需要mid");
            cookie = cookie + ";mid=" + tools.files.read().get("mid");
        }
        Map<String, String> header = game_login_headers;
        header.put("cookie", cookie);
        String response = sendGetRequest("https://api-takumi.mihoyo.com/auth/api/getCookieAccountInfoBySToken", header, null);
        JsonObject res = JsonParser.parseString(response).getAsJsonObject();
        if (res.get("retcode").getAsInt() != 0) {
            throw new RuntimeException("获取CookieToken失败,stoken已失效请重新抓取");
        }
        tools.files.write("cookie_token", res.get("data").getAsJsonObject().get("cookie_token").getAsString());
        return res.get("data").getAsJsonObject().get("cookie_token").getAsString();
    }

    /**
     * 获取device_fp
     *
     * @return device_fp -String
     */
    private static String getFp() {
        long min = 281474976710657L;
        long max = 4503599627370494L;
        Random random = new Random();
        long randomLong = min + (long) ((max - min + 1) * random.nextDouble());
        seedId = Long.toString(randomLong, 16);
        seedTime = String.valueOf(System.currentTimeMillis());
        Map<String, Object> body = new HashMap<>() {{
            put("seed_id", seedId);
            put("platform", "2");
            put("device_fp", generateRandomFp());
            put("device_id", deviceId);
            put("bbs_device_id", deviceId);
            put("ext_fields", getExtFields());
            put("app_name", "bbs_cn");
            put("seed_time", seedTime);
        }};
        String response = tools.sendPostRequest("https://public-data-api.mihoyo.com/device-fp/api/getFp", fp_headers, body);
        Gson j = new Gson();
        JsonObject jsonObject = j.fromJson(response, JsonObject.class);
        int retCode = jsonObject.get("retcode").getAsInt();
        if (retCode == 0) {
            JsonObject data = jsonObject.getAsJsonObject("data");
            return data.get("device_fp").getAsString();
        }
        throw new RuntimeException("获取设备指纹出错");
    }

    /**
     * 获取device_fp中的原始随机device_fp参数
     *
     * @return device_fp -String
     */
    private static String generateRandomFp() {
        String temp = deviceId.replace("-", "");
        return temp.substring(8, 21);
    }

    /**
     * 获取device_fp中的ext_fields参数
     *
     * @return ext_fields -String
     */
    private static String getExtFields() {
        String[] temp2 = deviceId.split("-");
        String aaid = temp2[0] + temp2[4].substring(0, 3) + "-" + temp2[4].substring(3, 6) + "-" + temp2[4].substring(6, 9) + temp2[1] + temp2[2] + temp2[3];
        Gson gson = new Gson();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("cpuType", "arm64-v8a");
        jsonObject.addProperty("romCapacity", "512");
        jsonObject.addProperty("productName", "ishtar");
        jsonObject.addProperty("romRemain", "459");
        jsonObject.addProperty("manufacturer", "Xiaomi");
        jsonObject.addProperty("appMemory", "512");
        jsonObject.addProperty("hostname", "xiaomi.eu");
        jsonObject.addProperty("screenSize", "1440x3022");
        jsonObject.addProperty("osVersion", "13");
        jsonObject.addProperty("aaid", aaid);
        jsonObject.addProperty("vendor", "中国电信");
        jsonObject.addProperty("accelerometer", "0.061016977x0.8362915x9.826724");
        jsonObject.addProperty("buildTags", "release-keys");
        jsonObject.addProperty("model", "2304FPN6DC");
        jsonObject.addProperty("brand", "Xiaomi");
        jsonObject.addProperty("oaid", deviceId);
        jsonObject.addProperty("hardware", "qcom");
        jsonObject.addProperty("deviceType", "ishtar");
        jsonObject.addProperty("devId", "REL");
        jsonObject.addProperty("serialNumber", "unknown");
        jsonObject.addProperty("buildTime", String.valueOf(System.currentTimeMillis()));
        jsonObject.addProperty("buildUser", "builder");
        jsonObject.addProperty("ramCapacity", "229481");
        jsonObject.addProperty("magnetometer", "80.64375x-14.1x77.90625");
        jsonObject.addProperty("display", "TKQ1.221114.001 release-keys");
        jsonObject.addProperty("ramRemain", "110308");
        jsonObject.addProperty("deviceInfo", "Xiaomi/ishtar/ishtar:13/TKQ1.221114.001/V14.0.17.0.TMACNXM:user/release-keys");
        jsonObject.addProperty("gyroscope", "0.0x0.0x0.0");
        jsonObject.addProperty("vaid", "7.9894776E-4x-1.3315796E-4x6.6578976E-4");
        jsonObject.addProperty("buildType", "user");
        jsonObject.addProperty("sdkVersion", 33);
        jsonObject.addProperty("board", "kalama");
        return gson.toJson(jsonObject);
    }

    /**
     * 获取DS
     *
     * @param salt 米游社salt
     **/
    private static String getDS(String salt) {
        byte[] digest;
        long currentTimeMillis = System.currentTimeMillis() / 1000;
        ArrayList<Character> arrayList = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            Random rand = new Random();
            char randomChar = chars.charAt(rand.nextInt(chars.length()));
            arrayList.add(randomChar);
        }
        StringBuilder r = new StringBuilder();
        for (Character c : arrayList) {
            r.append(c);
        }
        String random = r.toString();
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] bytes = ("salt=" + salt + "&t=" + currentTimeMillis + "&r=" + random).getBytes();
        messageDigest.update(bytes);
        digest = messageDigest.digest();
        StringBuilder d = new StringBuilder();
        for (byte b : digest) {
            d.append(String.format("%02x", b));
        }
        return currentTimeMillis + "," + random + "," + d;
    }

}

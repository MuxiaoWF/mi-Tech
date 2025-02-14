package com.muxiao;

import com.google.gson.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.muxiao.fixed.*;
import static com.muxiao.get_stoken_qrcode.getDS2;
import static com.muxiao.tools.sendGetRequest;
import static com.muxiao.tools.sendPostRequest;

public class user_info {
    private static final Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
    private static Map<String, String> params;

    private static String get_nickname(String game_biz, long uid) {
        List<Map<String, String>> user = get_user_game_role(game_biz);
        String nickname = "?";
        for (Map<String, String> map : user) {
            if (map.get("game_uid").equals(String.valueOf(uid))) {
                nickname = map.get("nickname");
                break;
            }
        }
        return nickname;
    }

    private static String seconds_to_time(int seconds) {
        int hour = seconds / 3600;
        int minute = (seconds % 3600) / 60;
        int second = seconds % 60;
        return String.format("%02d时%02d分%02d秒", hour, minute, second);
    }

    private static String getResponse(Map<String, String> user, String url) {
        String role_id = user.get("game_uid");
        String region = user.get("region");
        params = new HashMap<>() {{
            put("role_id", role_id);
            put("server", region);
        }};
        record_headers.put("DS", getDS2("", SALT_4X, "role_id=" + role_id + "&server=" + region));
        return sendGetRequest(url, record_headers, params);
    }

    private static String seconds_to_time_day(int seconds) {
        int day = seconds / 86400;
        int hour = (seconds % 86400) / 3600;
        int minute = (seconds % 86400 % 3600) / 60;
        int second = seconds % 86400 % 3600 % 60;
        return String.format("%02d天%02d时%02d分%02d秒", day, hour, minute, second);
    }

    private static String get_time(JsonObject start_or_end_time) {
        return start_or_end_time.get("year").getAsString()
                + "-" + start_or_end_time.get("month").getAsString()
                + "-" + start_or_end_time.get("day").getAsString()
                + " " + start_or_end_time.get("hour").getAsString()
                + ":" + start_or_end_time.get("minute").getAsString()
                + ":" + start_or_end_time.get("second").getAsString();
    }

    private static JsonObject captcha(JsonObject res, String url, String game_name) {
        if (res.get("retcode").getAsInt() == 1034) {
            record_headers.put("x-rpc-challenge_path", url);
            record_headers.put("x-rpc-challenge_game", name_to_game_num_id(game_name));
            String[] temp = start.start_service2(record_headers);
            if (temp != null) {
                record_headers.remove("x-rpc-challenge_path");
                record_headers.remove("x-rpc-challenge_game");
                record_headers.put("x-rpc-challenge", temp[0]);
                String response = sendGetRequest(url, record_headers, params);
                if (new Gson().fromJson(response, JsonObject.class).get("retcode").getAsInt() == 0) {
                    record_headers.remove("x-rpc-challenge");
                    return JsonParser.parseString(response).getAsJsonObject();
                }
            }
            throw new RuntimeException("获取失败:验证码");
        } else if (res.get("retcode").getAsInt() == 0) {
            return res;
        }
        throw new RuntimeException("获取失败" + res);
    }

    /**
     * 获取指定游戏的所有角色信息
     *
     * @param game_biz String
     * @return List&lt Map &lt String, String&gt&gt 包含nickname和level
     */
    private static List<Map<String, String>> get_user_game_role(String game_biz) {
        Map<String, String> params = new HashMap<>() {{
            put("game_biz", game_biz);
        }};
        String response = sendGetRequest("https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie", record_headers, params);
        JsonObject res = JsonParser.parseString(response).getAsJsonObject();
        List<Map<String, String>> users = new ArrayList<>();
        if (res.get("retcode").getAsInt() == 0) {
            JsonArray list = res.getAsJsonObject("data").getAsJsonArray("list");
            for (JsonElement jsonElement : list) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                Map<String, String> temp = new HashMap<>() {{
                    put("nickname", jsonObject.get("nickname").getAsString());
                    put("level", jsonObject.get("level").getAsString());
                    put("game_uid", jsonObject.get("game_uid").getAsString());
                }};
                users.add(temp);
            }
        } else throw new RuntimeException("get_user_game_role获取失败" + response);
        return users;
    }

    /**
     * 返回当前账号所有游戏的简要json数据
     *
     * @return String -json
     */
    public static String game_record_card() {
        Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
        record_headers.put("x-rpc-platform", "2");
        Map<String, String> params = new HashMap<>() {{
            put("uid", tools.files.read().get("stuid"));
        }};
        return sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/card/wapi/getGameRecordCard", record_headers, params);
    }

    /**
     * 返回当前账号所有游戏是否有角色
     *
     * @return Map<String, Boolean> -game_name -has_role
     */
    private static Map<String, Boolean> game_record_card_analysed() {
        String response = game_record_card();
        JsonObject res = JsonParser.parseString(response).getAsJsonObject();
        Map<String, Boolean> map = new HashMap<>();
        if (res.get("retcode").getAsInt() == 0) {
            JsonArray list = res.getAsJsonObject("data").getAsJsonArray("list");
            for (JsonElement jsonElement : list) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                map.put(jsonObject.get("game_name").getAsString(), jsonObject.get("has_role").getAsBoolean());
            }
        } else throw new RuntimeException("game_record_card获取失败" + response);
        return map;
    }

    protected static boolean no_has_role(String game_name) {
        Map<String, Boolean> map = game_record_card_analysed();
        if (!map.containsKey(game_name))
            return true;
        return !map.get(game_name);
    }

    public static class Genshin {
        /**
         * 获取世界探索的信息（原始json String的List-防止多用户），包含：基本信息，探索度，宝箱数，神瞳数，秘境数，深境螺旋，幻想真境剧诗等
         *
         * @return List -String
         */
        public static List<String> world() {
            if (no_has_role("原神"))
                return List.of("当前账号无角色");
            List<Map<String, String>> genshins = tools.files.read(name_to_game_id("原神"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> genshin : genshins) {
                String role_id = genshin.get("game_uid");
                String region = genshin.get("region");
                Map<String, String> params = new HashMap<>() {{
                    put("role_id", role_id);
                    put("server", region);
                }};
                String response = sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/index", record_headers, params);
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                if (res.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取世界探索的信息（文本 String），包含：基本信息，活跃天数，神瞳数，角色数，秘境数，深境螺旋，幻想真境剧诗，宝箱数，探索度等
         *
         * @return String
         */
        public static String world_analysed() {
            List<String> responses = world();
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                JsonObject role = data.getAsJsonObject("role");
                String name = role.get("nickname").getAsString();
                int level = role.get("level").getAsInt();
                String user_icon = role.get("game_head_icon").getAsString();
                JsonObject stats = data.getAsJsonObject("stats");
                int active_day_number = stats.get("active_day_number").getAsInt();
                int achievement_number = stats.get("achievement_number").getAsInt();
                int anemoculus_number = stats.get("anemoculus_number").getAsInt();
                int geoculus_number = stats.get("geoculus_number").getAsInt();
                int avatar_number = stats.get("avatar_number").getAsInt();
                int way_point_number = stats.get("way_point_number").getAsInt();
                int domain_number = stats.get("domain_number").getAsInt();
                String spiral_abyss = stats.get("spiral_abyss").getAsString();
                int precious_chest_number = stats.get("precious_chest_number").getAsInt();
                int luxurious_chest_number = stats.get("luxurious_chest_number").getAsInt();
                int exquisite_chest_number = stats.get("exquisite_chest_number").getAsInt();
                int common_chest_number = stats.get("common_chest_number").getAsInt();
                int electricculus_number = stats.get("electroculus_number").getAsInt();
                int magic_chest_number = stats.get("magic_chest_number").getAsInt();
                int dendroculus_number = stats.get("dendroculus_number").getAsInt();
                int hydroculus_number = stats.get("hydroculus_number").getAsInt();
                int pyroculus_number = stats.get("pyroculus_number").getAsInt();
                int full_fetter_avatar_num = stats.get("full_fetter_avatar_num").getAsInt();
                JsonArray world_explorations = data.getAsJsonArray("world_explorations");
                sb.append("旅行者 ").append(name).append(" ：\n");
                sb.append("等级：").append(level).append("\n");
                sb.append("活跃天数：").append(active_day_number).append("\n");
                sb.append("成就数：").append(achievement_number).append("\n");
                sb.append("风神瞳：").append(anemoculus_number).append("\n");
                sb.append("岩神瞳：").append(geoculus_number).append("\n");
                sb.append("雷神瞳：").append(electricculus_number).append("\n");
                sb.append("草神瞳：").append(dendroculus_number).append("\n");
                sb.append("水神瞳：").append(hydroculus_number).append("\n");
                sb.append("火神瞳：").append(pyroculus_number).append("\n");
                sb.append("角色数：").append(avatar_number).append("\n");
                sb.append("满好感角色数：").append(full_fetter_avatar_num).append("\n");
                sb.append("传送锚点：").append(way_point_number).append("\n");
                sb.append("秘境：").append(domain_number).append("\n");
                sb.append("深境螺旋：").append(spiral_abyss).append("\n");
                int combat = stats.getAsJsonObject("role_combat").get("max_round_id").getAsInt();
                if (combat > 0) sb.append("幻想真境剧诗：第").append(combat).append("幕").append("\n");
                sb.append("珍贵宝箱数：").append(precious_chest_number).append("\n");
                sb.append("华丽宝箱数：").append(luxurious_chest_number).append("\n");
                sb.append("精致宝箱数：").append(exquisite_chest_number).append("\n");
                sb.append("普通宝箱数：").append(common_chest_number).append("\n");
                sb.append("奇馈宝箱数：").append(magic_chest_number).append("\n");

                sb.append("\n地区探索：\n");
                Map<String, StringBuilder> sb_list = new HashMap<>();
                for (JsonElement world_exploration : world_explorations) {
                    StringBuilder builder = new StringBuilder();
                    JsonObject world_exploration_object = world_exploration.getAsJsonObject();
                    int world_type = world_exploration_object.get("world_type").getAsInt();
                    int world_level = world_exploration_object.get("level").getAsInt();
                    int exploration_percentage = world_exploration_object.get("exploration_percentage").getAsInt();
                    String region_name = world_exploration_object.get("name").getAsString();
                    builder.append(region_name).append("：探索度 ").append((double) exploration_percentage / 10).append("% \n");
                    if (world_type != 1) {
                        int seven_statue_level = world_exploration_object.get("seven_statue_level").getAsInt();
                        builder.append("  七天神像等级：").append(seven_statue_level).append("\n");
                        JsonElement natan_reputation = world_exploration_object.get("natan_reputation");
                        if (natan_reputation == null || natan_reputation instanceof JsonNull)
                            builder.append("  声望等级：").append(world_level).append("\n");
                        else {
                            JsonObject natan_reputation_object = natan_reputation.getAsJsonObject();
                            JsonArray tribal_list = natan_reputation_object.getAsJsonArray("tribal_list");
                            builder.append("  部落等级：\n");
                            for (JsonElement tribal : tribal_list) {
                                JsonObject tribal_object = tribal.getAsJsonObject();
                                String tribal_name = tribal_object.get("name").getAsString();
                                int tribal_level = tribal_object.get("level").getAsInt();
                                builder.append("    ").append(tribal_name).append("：").append(tribal_level).append("\n");
                            }
                        }
                    }
                    JsonArray offerings = world_exploration_object.getAsJsonArray("offerings");
                    if (offerings != null && !offerings.isEmpty()) {
                        builder.append("  地区供奉：\n");
                        for (JsonElement offering : offerings) {
                            JsonObject offering_object = offering.getAsJsonObject();
                            String offering_name = offering_object.get("name").getAsString();
                            int offering_level = offering_object.get("level").getAsInt();
                            builder.append("    ").append(offering_name).append("：").append(offering_level).append("\n");
                        }
                    }
                    JsonArray area_exploration_list = world_exploration_object.getAsJsonArray("area_exploration_list");
                    if (area_exploration_list != null && !area_exploration_list.isEmpty()) {
                        builder.append("  地区细分：\n");
                        for (JsonElement area_exploration : area_exploration_list) {
                            JsonObject area_exploration_object = area_exploration.getAsJsonObject();
                            int area_exploration_percentage = area_exploration_object.get("exploration_percentage").getAsInt();
                            String area_name = area_exploration_object.get("name").getAsString();
                            builder.append("    ").append(area_name).append("：").append((double) area_exploration_percentage / 10).append("% \n");
                        }
                    }
                    JsonArray boss_list = world_exploration_object.getAsJsonArray("boss_list");
                    if (boss_list != null && !boss_list.isEmpty()) {
                        builder.append("  强敌挑战次数：\n");
                        for (JsonElement boss : boss_list) {
                            JsonObject boss_object = boss.getAsJsonObject();
                            String boss_name = boss_object.get("name").getAsString();
                            int kill_num = boss_object.get("kill_num").getAsInt();
                            builder.append("    ").append(boss_name).append("：").append(kill_num).append("\n");
                        }
                    }
                    int parent_id = world_exploration_object.get("parent_id").getAsInt();
                    int id = world_exploration_object.get("id").getAsInt();
                    sb_list.put(Arrays.toString(new int[]{id, parent_id}), builder);
                }
                for (String ints : new ArrayList<>(sb_list.keySet())) {
                    int parent_id = Integer.parseInt(ints.replace("[", "").replace("]", "").replace(" ", "").split(",")[1]);
                    if (parent_id != 0) {
                        sb_list.put(Arrays.toString(new int[]{parent_id, 0}), sb_list.get(Arrays.toString(new int[]{parent_id, 0})).insert(sb_list.get(Arrays.toString(new int[]{parent_id, 0})).indexOf("\n") + 1, "  " + sb_list.get(ints)));
                        sb_list.remove(ints);
                    }
                }
                for (String ints : new ArrayList<>(sb_list.keySet())) {
                    sb.append(sb_list.get(ints));
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 获取每日信息（原始json String的List-防止多用户），包含：当前树脂/树脂恢复时间，已完成委托/委托总数，洞天宝钱恢复时间，周本奖励次数等
         *
         * @return String
         */
        public static List<String> day() {
            if (no_has_role("原神"))
                return List.of("当前账号无角色");
            List<Map<String, String>> genshins = tools.files.read(name_to_game_id("原神"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> genshin : genshins) {
                String response = getResponse(genshin, "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/dailyNote");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/dailyNote", "原神");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取每日信息（文本 String），包含：当前树脂/树脂恢复时间，已完成委托/委托总数，洞天宝钱恢复时间，周本奖励次数等
         *
         * @return String
         */
        public static String day_analysed() {
            List<String> reses = day();
            List<Map<String, String>> genshins = tools.files.read(name_to_game_id("原神"));
            StringBuilder sb = new StringBuilder();
            for (String res : reses) {
                String nickname = genshins.get(reses.indexOf(res)).get("nickname");
                JsonObject jsonObject = JsonParser.parseString(res).getAsJsonObject();
                JsonObject data = jsonObject.getAsJsonObject("data");
                int current_resin = data.get("current_resin").getAsInt();
                int max_resin = data.get("max_resin").getAsInt();
                int resin_recovery_time = data.get("resin_recovery_time").getAsInt();
                int finished_task_num = data.get("finished_task_num").getAsInt();
                int total_task_num = data.get("total_task_num").getAsInt();
                boolean is_extra_task_reward_received = data.get("is_extra_task_reward_received").getAsBoolean();
                int remain_resin_discount_num = data.get("remain_resin_discount_num").getAsInt();
                int resin_discount_num_limit = data.get("resin_discount_num_limit").getAsInt();
                int current_expedition_num = data.get("current_expedition_num").getAsInt();
                int max_expedition_num = data.get("max_expedition_num").getAsInt();
                JsonArray expeditions = data.getAsJsonArray("expeditions");
                int current_home_coin = data.get("current_home_coin").getAsInt();
                int max_home_coin = data.get("max_home_coin").getAsInt();
                int home_coin_recovery_time = data.get("home_coin_recovery_time").getAsInt();
                JsonObject transformer = data.getAsJsonObject("transformer");
                int recovery_time = transformer.getAsJsonObject("recovery_time").get("Day").getAsInt() * 24 * 60 * 60 + transformer.getAsJsonObject("recovery_time").get("Hour").getAsInt() * 60 * 60 + transformer.getAsJsonObject("recovery_time").get("Minute").getAsInt() * 60 + transformer.getAsJsonObject("recovery_time").get("Second").getAsInt();
                String stored_attendance = data.getAsJsonObject("daily_task").get("stored_attendance").getAsString();
                int stored_attendance_refresh_countdown = data.getAsJsonObject("daily_task").get("stored_attendance_refresh_countdown").getAsInt();
                int remained_time = 0;
                int finished_expedition_num = 0;
                for (JsonElement expedition : expeditions) {
                    JsonObject expeditionObject = expedition.getAsJsonObject();
                    String status = expeditionObject.get("status").getAsString();
                    remained_time = Math.max(Integer.parseInt(expeditionObject.get("remained_time").getAsString()), remained_time);
                    finished_expedition_num += status.equals("Finished") ? 1 : 0;
                }
                sb.append("旅行者：").append(nickname).append("：\n");
                sb.append("当前树脂：").append(current_resin).append("/").append(max_resin).append("\n");
                sb.append("树脂恢复时间：").append(seconds_to_time(resin_recovery_time)).append("\n");
                sb.append("已完成委托：").append(finished_task_num).append("/").append(total_task_num).append("\n");
                sb.append("是否已领取每日委托奖励：").append(is_extra_task_reward_received ? "是" : "否").append("\n");
                sb.append("长效历练点数：").append(stored_attendance).append("\n");
                sb.append("长效历练点刷新剩余时间：").append(seconds_to_time_day(stored_attendance_refresh_countdown)).append("\n");
                sb.append("剩余周本奖励：").append(remain_resin_discount_num).append("/").append(resin_discount_num_limit).append("\n");
                sb.append("当前探索派遣：").append(current_expedition_num).append("/").append(max_expedition_num).append("\n");
                if (current_expedition_num > 0 && current_expedition_num != finished_expedition_num) {
                    sb.append("剩余探索派遣时间：").append(seconds_to_time(remained_time)).append("\n");
                    sb.append("已完成探索派遣：").append(finished_expedition_num).append("/").append(max_expedition_num).append("\n");
                } else sb.append("所有探索派遣已完成\n");
                if (current_home_coin < max_home_coin) {
                    sb.append("当前洞天宝钱：").append(current_home_coin).append("/").append(max_home_coin).append("\n");
                    sb.append("洞天宝钱恢复时间：").append(seconds_to_time(home_coin_recovery_time)).append("\n");
                } else sb.append("洞天宝钱已满\n");
                if (recovery_time > 0)
                    sb.append("参量质变器恢复时间：").append(seconds_to_time_day(recovery_time)).append("\n");
                else sb.append("参量质变器已恢复\n");
            }
            return sb.toString();
        }

        /**
         * 获取原神活动日历相关（原始json String的List-防止多用户），包含：卡池信息，活动等
         *
         * @return String
         */
        public static String calendar() {
            List<Map<String, String>> genshins = tools.files.read(name_to_game_id("原神"));
            Map<String, String> genshin = genshins.getFirst();
            String role_id = genshin.get("game_uid");
            String region = genshin.get("region");
            Map<String, Object> params = new HashMap<>() {{
                put("role_id", role_id);
                put("server", region);
            }};
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            record_headers.put("DS", getDS2("", SALT_4X, "role_id=" + role_id + "&server=" + region));
            String response = sendPostRequest("https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/act_calendar", record_headers, params);
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/act_calendar", "原神");
            if (jsonObject.get("message").getAsString().equals("OK")) {
                return response;
            }
            throw new RuntimeException("获取原神活动日历失败" + response);
        }

        /**
         * 获取原神活动日历相关（解析后的String），包含：卡池信息，活动等
         * //TODO: 暂时没有混池，等更新
         *
         * @return String
         */
        public static String calendar_analysed() {
            String response = calendar();
            StringBuilder sb = new StringBuilder();
            sb.append("原神活动日历：\n");
            JsonObject data = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("data");
            JsonArray avatar_card_pool_list = data.getAsJsonArray("avatar_card_pool_list");
            for (JsonElement avatar_card_pool : avatar_card_pool_list) {
                JsonObject avatar_card_pool_object = avatar_card_pool.getAsJsonObject();
                String version_name = avatar_card_pool_object.get("version_name").getAsString();
                String card_pool_name = avatar_card_pool_object.get("pool_name").getAsString();
                sb.append(version_name).append(card_pool_name).append("：\n");
                JsonArray avatars = avatar_card_pool_object.getAsJsonArray("avatars");
                StringBuilder _4 = new StringBuilder();
                StringBuilder _5 = new StringBuilder();
                for (JsonElement avatar : avatars) {
                    JsonObject avatar_object = avatar.getAsJsonObject();
                    String avatar_name = avatar_object.get("name").getAsString();
                    int rarity = avatar_object.get("rarity").getAsInt();
                    switch (rarity) {
                        case 4 -> _4.append(" ").append(avatar_name);
                        case 5 -> _5.append(" ").append(avatar_name);
                    }
                }
                sb.append("  5⭐：").append(_5).append("  4⭐：").append(_4).append("\n");
                String start_time = get_time(avatar_card_pool_object.getAsJsonObject("start_time"));
                String end_time = get_time(avatar_card_pool_object.getAsJsonObject("end_time"));
                sb.append("  活动时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
            }
            sb.append("\n");
            JsonArray weapon_card_pool_list = data.getAsJsonArray("weapon_card_pool_list");
            for (JsonElement weapon_card_pool : weapon_card_pool_list) {
                JsonObject weapon_card_pool_object = weapon_card_pool.getAsJsonObject();
                String version_name = weapon_card_pool_object.get("version_name").getAsString();
                String card_pool_name = weapon_card_pool_object.get("pool_name").getAsString();
                sb.append(version_name).append(card_pool_name).append("：\n");
                JsonArray weapons = weapon_card_pool_object.getAsJsonArray("weapon");
                StringBuilder _4 = new StringBuilder();
                StringBuilder _5 = new StringBuilder();
                for (JsonElement weapon : weapons) {
                    JsonObject weapon_object = weapon.getAsJsonObject();
                    String avatar_name = weapon_object.get("name").getAsString();
                    int rarity = weapon_object.get("rarity").getAsInt();
                    switch (rarity) {
                        case 4 -> _4.append(" ").append(avatar_name);
                        case 5 -> _5.append(" ").append(avatar_name);
                    }
                }
                sb.append("  5⭐：").append(_5).append("  4⭐：").append(_4).append("\n");
                String start_time = get_time(weapon_card_pool_object.getAsJsonObject("start_time"));
                String end_time = get_time(weapon_card_pool_object.getAsJsonObject("end_time"));
                sb.append("  活动时间：").append(start_time).append("~").append(end_time).append("\n");
            }
            JsonElement mixed_card_pool_list = data.get("mixed_card_pool_list");
            if (!mixed_card_pool_list.isJsonNull() && !mixed_card_pool_list.getAsJsonArray().isEmpty()) {
                sb.append("\n");
                for (JsonElement mixed_card_pool : mixed_card_pool_list.getAsJsonArray()) {
                    JsonObject mixed_card_pool_object = mixed_card_pool.getAsJsonObject();
                    // TODO:  暂时没有混池，等更新
                }
            }
            sb.append("\n");
            JsonArray act_list = data.getAsJsonArray("act_list");
            for (JsonElement act : act_list) {
                JsonObject act_object = act.getAsJsonObject();
                String act_name = act_object.get("name").getAsString();
                sb.append(act_name).append("：").append("\n");
                JsonElement descE = act_object.get("desc");
                sb.append(descE.isJsonNull() || descE.getAsString().isEmpty() ? "" : "  " + descE.getAsString() + "\n");
                int status = act_object.get("status").getAsInt();
                boolean is_finished = act_object.get("is_finished").getAsBoolean();
                if (act_object.get("start_time").isJsonNull())
                    sb.append("  活动暂未开启并公布时间\n");
                else {
                    switch (status) {
                        case 1 -> sb.append(" 活动暂未开启").append("\n");
                        case 2 -> sb.append(" 活动进行中").append("\n");
                    }
                    if (is_finished)
                        sb.append(" 已完成活动").append("\n");
                    String start_time = get_time(act_object.getAsJsonObject("start_time"));
                    String end_time = get_time(act_object.getAsJsonObject("end_time"));
                    sb.append("   活动时间：").append(start_time).append("~").append(end_time).append("\n");
                    JsonArray reward_list = act_object.getAsJsonArray("reward_list");
                    sb.append("   活动奖励：");
                    for (JsonElement reward : reward_list) {
                        JsonObject reward_object = reward.getAsJsonObject();
                        String reward_name = reward_object.get("name").getAsString();
                        int reward_num = reward_object.get("num").getAsInt();
                        sb.append(reward_name).append("x").append(reward_num).append("  ");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            JsonArray fixed_act_list = data.getAsJsonArray("fixed_act_list");
            for (JsonElement act : fixed_act_list) {
                JsonObject act_object = act.getAsJsonObject();
                String act_name = act_object.get("name").getAsString();
                sb.append(act_name).append("：").append("\n");
                JsonElement descE = act_object.get("desc");
                sb.append(descE.isJsonNull() || descE.getAsString().isEmpty() ? "" : "   " + descE.getAsString() + "\n");
                int status = act_object.get("status").getAsInt();
                boolean is_finished = act_object.get("is_finished").getAsBoolean();
                if (act_object.get("start_time").isJsonNull())
                    sb.append(" 活动暂未开启并公布时间\n");
                else {
                    switch (status) {
                        case 1 -> sb.append(" 活动暂未开启").append("\n");
                        case 2 -> sb.append(" 活动进行中").append("\n");
                    }
                    if (is_finished)
                        sb.append(" 已完成活动").append("\n");
                    String start_time = get_time(act_object.getAsJsonObject("start_time"));
                    String end_time = get_time(act_object.getAsJsonObject("end_time"));
                    sb.append("   活动时间：").append(start_time).append("~").append(end_time).append("\n");
                    JsonArray reward_list = act_object.getAsJsonArray("reward_list");
                    sb.append("   活动奖励：");
                    for (JsonElement reward : reward_list) {
                        JsonObject reward_object = reward.getAsJsonObject();
                        String reward_name = reward_object.get("name").getAsString();
                        int reward_num = reward_object.get("num").getAsInt();
                        sb.append(reward_name).append("x").append(reward_num).append("  ");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 获取七圣召唤相关（原始json String的List-防止多用户），包含：角色牌数/行动牌数
         *
         * @return String
         */
        public static List<String> TCG() {
            if (no_has_role("原神"))
                return List.of("当前账号无角色");
            List<Map<String, String>> genshins = tools.files.read(name_to_game_id("原神"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> genshin : genshins) {
                String response = getResponse(genshin, "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/gcg/basicInfo");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                res = captcha(res, "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/gcg/basicInfo", "原神");
                if (res.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取七圣召唤相关（文本 String），包含：角色牌数/行动牌数
         *
         * @return String
         */
        public static String TCG_analysed() {
            List<String> responses = TCG();
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();

                JsonObject data = res.get("data").getAsJsonObject();
                int level = data.get("level").getAsInt();
                String nickname = data.get("nickname").getAsString();
                int avatar_card_num_gained = data.get("avatar_card_num_gained").getAsInt();
                int avatar_card_num_total = data.get("avatar_card_num_total").getAsInt();
                int action_card_num_gained = data.get("action_card_num_gained").getAsInt();
                int action_card_num_total = data.get("action_card_num_total").getAsInt();
                sb.append("旅行者名称：").append(nickname).append("\n").append("等级：").append(level).append("\n")
                        .append("角色牌：").append(avatar_card_num_gained).append("/").append(avatar_card_num_total).append("\n")
                        .append("行动牌：").append(action_card_num_gained).append("/").append(action_card_num_total).append("\n");
            }
            return sb.toString();
        }

        /**
         * 返回桌面小组件的json数据(好问题不能多用户) -可用于day的下位替代
         * 包含：树脂，派遣，每日委托，洞天宝钱等
         *
         * @return String
         */
        public static String day2_widget() {
            if (no_has_role("原神"))
                return ("当前账号无角色");
            Map<String, String> widget_headers = new HashMap<>(fixed.widget_headers);
            widget_headers.put("cookie", "stuid=" + tools.files.read().get("stuid") + ";stoken=" + tools.files.read().get("stoken") + ";mid=" + tools.files.read().get("mid") + ";");
            String response = sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/genshin/aapi/widget/v2", widget_headers, null);
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            res = captcha(res, "https://api-takumi-record.mihoyo.com/game_record/genshin/aapi/widget/v2", "原神");
            if (res.get("message").getAsString().equals("OK")) {
                return response;
            }
            throw new RuntimeException("day2获取出错" + response);
        }

        /**
         * 返回桌面小组件的文本数据 -可用于day_analysed的下位替代
         * 包含：树脂，派遣，每日委托，洞天宝钱等
         *
         * @return String
         */
        public static String day2_widget_analysed() {
            String response = day2_widget();
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            JsonObject data = res.get("data").getAsJsonObject();
            int current_resin = data.get("current_resin").getAsInt();
            int max_resin = data.get("max_resin").getAsInt();
            int resin_recovery_time = Integer.parseInt(data.get("resin_recovery_time").getAsString());
            int finished_task_num = data.get("finished_task_num").getAsInt();
            int total_task_num = data.get("total_task_num").getAsInt();
            boolean is_extra_task_reward_received = data.get("is_extra_task_reward_received").getAsBoolean();
            int current_expedition_num = data.get("current_expedition_num").getAsInt();
            int max_expedition_num = data.get("max_expedition_num").getAsInt();
            JsonArray expeditions = data.getAsJsonArray("expeditions");
            int current_home_coin = data.get("current_home_coin").getAsInt();
            int max_home_coin = data.get("max_home_coin").getAsInt();
            int finish_expedition_num = 0;
            for (JsonElement expedition : expeditions) {
                JsonObject expedition_object = expedition.getAsJsonObject();
                if (!expedition_object.get("status").getAsString().equals("Ongoing")) {
                    finish_expedition_num++;
                }
            }
            return "当前树脂：" + current_resin + "/" + max_resin + "\n" +
                    "树脂恢复时间：" + seconds_to_time(resin_recovery_time) + "\n" +
                    "当前洞天宝钱：" + current_home_coin + "/" + max_home_coin + "\n" +
                    "完成委托：" + finished_task_num + "/" + total_task_num + "  " + (is_extra_task_reward_received ? "每日委托奖励已领取" : "每日委托奖励未领取") + "\n" +
                    "当前派遣：" + current_expedition_num + "/" + max_expedition_num + "  " + "已完成派遣：" + finish_expedition_num + "个\n";
        }

        /**
         * 返回活动日历小组件的json数据(好问题不能多用户)，可能可以做day2_widget的下位替代
         * 包含：树脂，每日委托，洞天宝钱，活动及奖励等（比day2多了活动及奖励，少了派遣）
         *
         * @return String
         */
        public static String calendar2_widget() {
            if (no_has_role("原神"))
                return ("当前账号无角色");
            Map<String, String> widget_headers = new HashMap<>(fixed.widget_headers);
            widget_headers.put("cookie", "stuid=" + tools.files.read().get("stuid") + ";stoken=" + tools.files.read().get("stoken") + ";mid=" + tools.files.read().get("mid") + ";");
            String response = sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/genshin/aapi/act_calendar/widget", widget_headers, null);
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            if (res.get("message").getAsString().equals("OK")) {
                return response;
            }
            throw new RuntimeException("calendar2获取出错" + response);
        }

        /**
         * 返回活动日历小组件的文本数据，可能可以做day2_widget的下位替代
         * 包含：树脂，每日委托，洞天宝钱，活动及奖励等（比day2多了活动及奖励，少了派遣）
         *
         * @return String
         */
        public static String calendar2_widget_analysed() {
            String response = calendar2_widget();
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            JsonObject data = res.get("data").getAsJsonObject();
            int current_resin = data.get("current_resin").getAsInt();
            int max_resin = data.get("max_resin").getAsInt();
            int resin_recovery_time = Integer.parseInt(data.get("resin_recovery_time").getAsString());
            int finished_task_num = data.get("finished_task_num").getAsInt();
            int total_task_num = data.get("total_task_num").getAsInt();
            boolean is_extra_task_reward_received = data.get("is_extra_task_reward_received").getAsBoolean();
            int current_home_coin = data.get("current_home_coin").getAsInt();
            int max_home_coin = data.get("max_home_coin").getAsInt();
            JsonArray act_list = data.getAsJsonArray("act_list");
            StringBuilder sb = new StringBuilder();
            for (JsonElement act : act_list) {
                JsonObject act_object = act.getAsJsonObject();
                String name = act_object.get("name").getAsString();
                long start_timestamp = Integer.parseInt(act_object.get("start_timestamp").getAsString()) * 1000L;
                String start = new SimpleDateFormat("MM月-dd日 HH:mm:ss").format(new Date(start_timestamp));
                long end_timestamp = Integer.parseInt(act_object.get("end_timestamp").getAsString()) * 1000L;
                String end = new SimpleDateFormat("MM月-dd日 HH:mm:ss").format(new Date(end_timestamp));
                sb.append(name).append("\n");
                JsonElement reward = act_object.get("reward");
                if (!reward.isJsonNull()) {
                    String reward_name = act_object.getAsJsonObject("reward").get("name").getAsString();
                    int reward_num = act_object.getAsJsonObject("reward").get("num").getAsInt();
                    sb.append("奖励：").append(reward_name).append("x").append(reward_num);
                } else sb.append("奖励为空");
                sb.append("\n持续时间：").append(start).append("~").append(end).append("\n");
            }
            return "当前树脂：" + current_resin + "/" + max_resin + "\n" +
                    "树脂恢复时间：" + seconds_to_time(resin_recovery_time) + "\n" +
                    "当前洞天宝钱：" + current_home_coin + "/" + max_home_coin + "\n" +
                    "完成委托：" + finished_task_num + "/" + total_task_num + "  " + (is_extra_task_reward_received ? "每日委托奖励已领取" : "每日委托奖励未领取") + "\n" +
                    "\n活动日历：\n" + sb;
        }

        /**
         * 月度收入信息（json源文件）
         *
         * @param month 格式为月（1月就是1,2月为2,以此类推，去年12月就直接12） -只能查最多前两个月
         * @return String的List-防止多用户
         */
        public static List<String> month_info(int month) {
            if (no_has_role("原神"))
                return List.of("当前账号无角色");
            List<Map<String, String>> genshins = tools.files.read(name_to_game_id("原神"));
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            List<String> responses = new ArrayList<>();
            for (Map<String, String> genshin : genshins) {
                Map<String, String> params = new HashMap<>() {{
                    put("bind_uid", genshin.get("game_uid"));
                    put("bind_region", genshin.get("region"));
                    put("bbs_presentation_style", "fullscreen");
                    put("bbs_auth_required", "true");
                    put("utm_source", "bbs");
                    put("utm_medium", "mys");
                    put("utm_campaign", "icon");
                    put("month", String.valueOf(month));
                }};
                String response = sendGetRequest("https://act-hk4e-api.mihoyo.com/event/ys_ledger/monthInfo", record_headers, params);
                responses.add(response);
            }
            return responses;
        }

        /**
         * 返回月度收入信息（简要分析后）
         *
         * @param month 格式为月（1月就是1,2月为2,以此类推，去年12月就直接12） -只能查最多前两个月
         * @return String
         */
        public static String month_info_analysed(int month) {
            List<String> responses = month_info(month);
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject data = JsonParser.parseString(response).getAsJsonObject().get("data").getAsJsonObject();
                long uid = data.get("uid").getAsLong();
                String nickname = data.get("nickname").getAsString();
                int current_month = data.get("month").getAsInt();
                int data_month = data.get("data_month").getAsInt();
                int data_last_month = data.get("data_last_month").getAsInt();
                sb.append("原神: UID ").append(uid).append(" 旅行者：").append(nickname).append("\n")
                        .append("当前月份：").append(current_month).append("月 ; 查询月份：").append(data_month).append("月 ; 查询月份上个月为：").append(data_last_month).append("月\n");
                if (data_month == current_month) {
                    JsonObject day_data = data.getAsJsonObject("day_data");
                    int current_primogems = day_data.get("current_primogems").getAsInt();
                    int current_mora = day_data.get("current_mora").getAsInt();
                    int last_primogems = day_data.get("last_primogems").getAsInt();
                    int last_mora = day_data.get("last_mora").getAsInt();
                    sb.append("今日原石收入：").append(current_primogems).append("  昨日原石收入：").append(last_primogems).append("  增长度 ")
                            .append(new BigDecimal(current_primogems - last_primogems).multiply(new BigDecimal(100)).divide(new BigDecimal(last_primogems), 2, RoundingMode.HALF_UP)).append("%\n")
                            .append("今日摩拉收入：").append(current_mora).append("  昨日摩拉收入：").append(last_mora).append("  增长度 ")
                            .append(new BigDecimal(current_mora - last_mora).multiply(new BigDecimal(100)).divide(new BigDecimal(last_mora), 2, RoundingMode.HALF_UP)).append("%\n");
                    sb.append("\n");
                }
                JsonObject month_data = data.getAsJsonObject("month_data");
                int current_primogems = month_data.get("current_primogems").getAsInt();
                int current_mora = month_data.get("current_mora").getAsInt();
                int last_primogems = month_data.get("last_primogems").getAsInt();
                int last_mora = month_data.get("last_mora").getAsInt();
                sb.append(data_month).append("月原石收入：").append(current_primogems).append("  上月原石收入：").append(last_primogems).append("  增长度 ")
                        .append(new BigDecimal(current_primogems - last_primogems).multiply(new BigDecimal(100)).divide(new BigDecimal(last_primogems), 2, RoundingMode.HALF_UP)).append("%\n")
                        .append(data_month).append("月摩拉收入：").append(current_mora).append("  上月摩拉收入：").append(last_mora).append("  增长度 ")
                        .append(new BigDecimal(current_mora - last_mora).multiply(new BigDecimal(100)).divide(new BigDecimal(last_mora), 2, RoundingMode.HALF_UP)).append("%\n");
                sb.append("\n原石收入来源：\n");
                for (JsonElement group : month_data.getAsJsonArray("group_by")) {
                    JsonObject group_data = group.getAsJsonObject();
                    sb.append(group_data.get("action").getAsString()).append(" 收入：").append(group_data.get("num").getAsInt())
                            .append(" 收入占比：").append(group_data.get("percent").getAsInt()).append("%\n");
                }
            }
            return sb.toString();
        }
    }

    public static class Hkrpg {
        /**
         * 返回不可知域的运行记录
         *
         * @return String -json的List-防止多用户
         */
        public static List<String> rogue_magic() {
            if (no_has_role("崩坏：星穹铁道"))
                return List.of("当前账号无角色");
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> hkrpg : hkrpgs) {
                String response = getResponse(hkrpg, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/rogue_magic");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/rogue_magic", "星铁");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 返回拆分宇宙的运行记录
         *
         * @return String -json的List-防止多用户
         */
        public static List<String> rogue_tourn() {
            if (no_has_role("崩坏：星穹铁道"))
                return List.of("当前账号无角色");
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> hkrpg : hkrpgs) {
                String response = getResponse(hkrpg, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/rogue_tourn");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/rogue_tourn", "星铁");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 返回每日战绩数据 json源文件
         * 包含：开拓力，派遣，每日实训，模拟宇宙等
         *
         * @return String -json的List-防止多用户
         */
        public static List<String> day() {
            if (no_has_role("崩坏：星穹铁道"))
                return List.of("当前账号无角色");
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> hkrpg : hkrpgs) {
                String response = getResponse(hkrpg, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/note");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/note", "星铁");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 返回每日战绩数据 简要分析
         * 包含：开拓力，派遣，每日实训，模拟宇宙等
         *
         * @return String
         */
        public static String day_analysed() {
            List<String> responses = day();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            for (String response : responses) {
                String name = hkrpgs.get(responses.indexOf(response)).get("nickname");
                String game_uid = hkrpgs.get(responses.indexOf(response)).get("game_uid");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                int current_stamina = data.get("current_stamina").getAsInt();
                int max_stamina = data.get("max_stamina").getAsInt();
                int stamina_recover_time = data.get("stamina_recover_time").getAsInt();
                int accepted_expedition_num = data.get("accepted_epedition_num").getAsInt();//他喵的少一个x
                int total_expedition_num = data.get("total_expedition_num").getAsInt();
                JsonArray expeditions = data.getAsJsonArray("expeditions");
                int current_train_score = data.get("current_train_score").getAsInt();
                int max_train_score = data.get("max_train_score").getAsInt();
                int current_rogue_score = data.get("current_rogue_score").getAsInt();
                int max_rogue_score = data.get("max_rogue_score").getAsInt();
                int current_reserve_stamina = data.get("current_reserve_stamina").getAsInt();
                boolean rogue_tourn_weekly_unlocked = data.get("rogue_tourn_weekly_unlocked").getAsBoolean();
                int rogue_tourn_weekly_max = data.get("rogue_tourn_weekly_max").getAsInt();
                int rogue_tourn_weekly_cur = data.get("rogue_tourn_weekly_cur").getAsInt();
                int weekly_cocoon_cnt = data.get("weekly_cocoon_cnt").getAsInt();
                int weekly_cocoon_limit = data.get("weekly_cocoon_limit").getAsInt();
                boolean is_reserve_stamina_full = data.get("is_reserve_stamina_full").getAsBoolean();
                boolean rogue_tourn_exp_is_full = data.get("rogue_tourn_exp_is_full").getAsBoolean();
                int finished_expedition_num = 0;
                for (JsonElement expedition : expeditions) {
                    JsonObject expedition_object = expedition.getAsJsonObject();
                    if (!expedition_object.get("status").getAsString().equals("Finished")) {
                        finished_expedition_num++;
                    }
                }
                sb.append("开拓者 ").append(name).append("  UID ").append(game_uid).append("：\n")
                        .append("当前开拓力： ").append(current_stamina).append("/").append(max_stamina).append("\n")
                        .append("开拓力恢复时间：").append(seconds_to_time(stamina_recover_time)).append("\n")
                        .append("当前派遣： ").append(accepted_expedition_num).append("/").append(total_expedition_num).append("  已完成派遣：").append(finished_expedition_num).append("个\n")
                        .append("每日实训： ").append(current_train_score).append("/").append(max_train_score).append("\n")
                        .append("周本次数： ").append(weekly_cocoon_cnt).append("/").append(weekly_cocoon_limit).append("\n")
                        .append("模拟宇宙每周积分：").append(current_rogue_score).append("/").append(max_rogue_score).append(rogue_tourn_exp_is_full ? " 积分已满" : "").append("\n")
                        .append("当前储备开拓力：").append(current_reserve_stamina).append(is_reserve_stamina_full ? " 储备已满" : "").append("\n")
                        .append("拆分宇宙积分：").append(rogue_tourn_weekly_unlocked ? rogue_tourn_weekly_cur + "/" + rogue_tourn_weekly_max : "未解锁").append("\n");
            }
            return sb.toString();
        }

        /**
         * 返回桌面小组件的json数据(好问题不能多用户) -可用于day的下位替代
         * 包含：开拓力，派遣，每日实训，模拟宇宙等
         *
         * @return String
         */
        public static String day2_widget() {
            if (no_has_role("崩坏：星穹铁道"))
                return ("当前账号无角色");
            Map<String, String> widget_headers = new HashMap<>(fixed.widget_headers);
            widget_headers.put("DS", getDS2("", SALT_6X, ""));
            widget_headers.put("cookie", "stuid=" + tools.files.read().get("stuid") + ";stoken=" + tools.files.read().get("stoken") + ";mid=" + tools.files.read().get("mid") + ";");
            String response = sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/aapi/widget", widget_headers, null);
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            if (res.get("message").getAsString().equals("OK")) {
                return response;
            }
            throw new RuntimeException("day2获取出错" + response);
        }

        /**
         * 返回桌面小组件的文本数据 -可用于day_analysed的下位替代
         * 包含：开拓力，派遣，每日实训，模拟宇宙等
         *
         * @return String
         */
        public static String day2_widget_analysed() {
            String response = day2_widget();
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            JsonObject data = res.get("data").getAsJsonObject();
            int current_stamina = data.get("current_stamina").getAsInt();
            int max_stamina = data.get("max_stamina").getAsInt();
            int stamina_recover_time = data.get("stamina_recover_time").getAsInt();
            int accepted_expedition_num = data.get("accepted_expedition_num").getAsInt();
            int total_expedition_num = data.get("total_expedition_num").getAsInt();
            JsonArray expeditions = data.getAsJsonArray("expeditions");
            int current_train_score = data.get("current_train_score").getAsInt();
            int max_train_score = data.get("max_train_score").getAsInt();
            int current_rogue_score = data.get("current_rogue_score").getAsInt();
            int max_rogue_score = data.get("max_rogue_score").getAsInt();
            int current_reserve_stamina = data.get("current_reserve_stamina").getAsInt();
            boolean rogue_tourn_weekly_unlocked = data.get("rogue_tourn_weekly_unlocked").getAsBoolean();
            int rogue_tourn_weekly_max = data.get("rogue_tourn_weekly_max").getAsInt();
            int rogue_tourn_weekly_cur = data.get("rogue_tourn_weekly_cur").getAsInt();
            int finished_expedition_num = 0;
            for (JsonElement expedition : expeditions) {
                JsonObject expedition_object = expedition.getAsJsonObject();
                if (!expedition_object.get("status").getAsString().equals("Finished")) {
                    finished_expedition_num++;
                }
            }
            return "当前开拓力： " + current_stamina + "/" + max_stamina + "\n" +
                    "开拓力恢复时间：" + seconds_to_time(stamina_recover_time) + "\n" +
                    "当前派遣： " + accepted_expedition_num + "/" + total_expedition_num + "  已完成派遣：" + finished_expedition_num + "个\n" +
                    "每日实训： " + current_train_score + "/" + max_train_score + "\n" +
                    "模拟宇宙每周积分：" + current_rogue_score + "/" + max_rogue_score + "\n" +
                    "当前储备开拓力：" + current_reserve_stamina + (current_reserve_stamina == 2400 ? " 储备已满" : "") + "\n" +
                    "拆分宇宙积分：" + (rogue_tourn_weekly_unlocked ? rogue_tourn_weekly_cur + "/" + rogue_tourn_weekly_max : "未解锁") + "\n";
        }

        /**
         * 获取世界探索的信息（原始json String的List-防止多用户），包含：角色信息，宝箱数等
         *
         * @return String的List-防止多用户
         */
        public static List<String> world() {
            if (no_has_role("崩坏：星穹铁道"))
                return List.of("当前账号无角色");
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> hkrpg : hkrpgs) {
                String response = getResponse(hkrpg, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/index");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                res = captcha(res, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/index", "星铁");
                if (res.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取世界探索的信息（文本 String），包含：角色信息，宝箱数等
         *
         * @return String
         */
        public static String world_analysed() {
            List<String> responses = world();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            for (String response : responses) {
                String nickname = hkrpgs.get(responses.indexOf(response)).get("nickname");
                String uid = hkrpgs.get(responses.indexOf(response)).get("game_uid");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject stats = res.get("data").getAsJsonObject().getAsJsonObject("stats");
                int active_days = stats.get("active_days").getAsInt();
                int avatar_num = stats.get("avatar_num").getAsInt();
                int achievement_num = stats.get("achievement_num").getAsInt();
                int chest_num = stats.get("chest_num").getAsInt();
                int dream_paster_num = stats.get("dream_paster_num").getAsInt();
                String abyss_process = stats.get("abyss_process").getAsString();
                sb.append("开拓者 ").append(nickname).append("  UID ").append(uid).append("：\n")
                        .append("活跃天数：").append(active_days).append("\n")
                        .append("角色数：").append(avatar_num).append("\n")
                        .append("成就数：").append(achievement_num).append("\n")
                        .append("宝箱数：").append(chest_num).append("\n")
                        .append("梦境护照贴纸：").append(dream_paster_num).append("\n")
                        .append("深渊：").append(abyss_process).append("\n");

            }
            return sb.toString();
        }

        /**
         * 获取活动的信息（原始json String的List-防止多用户），包含：卡池信息，活动信息及奖励等
         *
         * @return String的List-防止多用户 --虽然好像并没有什么用
         */
        public static List<String> calendar() {
            if (no_has_role("崩坏：星穹铁道"))
                return List.of("当前账号无角色");
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> hkrpg : hkrpgs) {
                String response = getResponse(hkrpg, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/get_act_calender");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/api/get_act_calender", "星铁");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取活动的信息（文本 String），包含：卡池信息，活动信息及奖励等
         *
         * @return String
         */
        public static String calendar_analysed() {
            List<String> responses = calendar();
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.get("data").getAsJsonObject();
                JsonArray activities = data.get("act_list").getAsJsonArray();
                for (JsonElement activity : activities) {
                    JsonObject activity_object = activity.getAsJsonObject();
                    String name = activity_object.get("name").getAsString();
                    String start_time = activity_object.getAsJsonObject("time_info").get("start_time").getAsString();
                    String end_time = activity_object.getAsJsonObject("time_info").get("end_time").getAsString();
                    String panel_desc = activity_object.get("panel_desc").getAsString();
                    int total_progress = activity_object.get("total_progress").getAsInt();
                    int current_progress = activity_object.get("current_progress").getAsInt();
                    sb.append(name).append("  ").append(current_progress).append("/").append(total_progress);
                    if (!Objects.equals(panel_desc, "")) sb.append("\n  ").append(panel_desc);
                    sb.append("\n  活动时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
                    JsonArray reward_list = activity_object.get("reward_list").getAsJsonArray();
                    sb.append("  活动奖励：").append("\n");
                    for (JsonElement reward : reward_list) {
                        JsonObject reward_object = reward.getAsJsonObject();
                        String reward_name = reward_object.get("name").getAsString();
                        String reward_num = reward_object.get("num").getAsString();
                        sb.append("    ").append(reward_name).append(" x ").append(reward_num);
                    }
                    sb.append("\n");
                }
                JsonArray challenges = data.get("challenge_list").getAsJsonArray();
                sb.append("\n当前挑战：");
                for (JsonElement challenge : challenges) {
                    JsonObject challenge_object = challenge.getAsJsonObject();
                    String name = challenge_object.get("name_mi18n").getAsString();
                    String start_time = challenge_object.getAsJsonObject("time_info").get("start_time").getAsString();
                    String end_time = challenge_object.getAsJsonObject("time_info").get("end_time").getAsString();
                    int total_progress = challenge_object.get("total_progress").getAsInt();
                    int current_progress = challenge_object.get("current_progress").getAsInt();
                    sb.append(name).append("  ").append(current_progress).append("/").append(total_progress)
                            .append("\n挑战时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
                    JsonArray reward_list = challenge_object.get("reward_list").getAsJsonArray();
                    sb.append("挑战奖励：").append("\n");
                    if (reward_list.isEmpty()) sb.delete(sb.length() - 6, sb.length());
                    for (JsonElement reward : reward_list) {
                        JsonObject reward_object = reward.getAsJsonObject();
                        String reward_name = reward_object.get("name").getAsString();
                        String reward_num = reward_object.get("num").getAsString();
                        sb.append("  ").append(reward_name).append(" x ").append(reward_num);
                    }
                    sb.append("\n");
                }
                JsonArray avatar_card_pool_list = data.get("avatar_card_pool_list").getAsJsonArray();
                sb.append("\n当前角色池：");
                for (JsonElement avatar_card_pool : avatar_card_pool_list) {
                    JsonObject avatar_card_pool_object = avatar_card_pool.getAsJsonObject();
                    String name = avatar_card_pool_object.get("name").getAsString();
                    String start_time = avatar_card_pool_object.getAsJsonObject("time_info").get("start_time").getAsString();
                    String end_time = avatar_card_pool_object.getAsJsonObject("time_info").get("end_time").getAsString();
                    sb.append(name).append("\n 持续时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
                    JsonArray avatar_list = avatar_card_pool_object.get("avatar_list").getAsJsonArray();
                    ArrayList<String> avatar5_name = new ArrayList<>();
                    ArrayList<String> avatar4_name = new ArrayList<>();
                    for (JsonElement avatar : avatar_list) {
                        JsonObject avatar_object = avatar.getAsJsonObject();
                        String rarity = avatar_object.get("rarity").getAsString();
                        String avatar_name = avatar_object.get("item_name").getAsString();
                        if (rarity.equals("5")) avatar5_name.add(avatar_name);
                        else avatar4_name.add(avatar_name);
                    }
                    if (!avatar5_name.isEmpty()) {
                        sb.append("  5⭐角色：");
                        for (String avatar_name : avatar5_name)
                            sb.append(avatar_name).append("  ");
                    }
                    sb.append("\n");
                    if (!avatar4_name.isEmpty()) {
                        sb.append("  4⭐角色：");
                        for (String avatar_name : avatar4_name)
                            sb.append(avatar_name).append("  ");
                    }
                    sb.append("\n");
                }
                JsonArray equip_card_pool_list = data.get("equip_card_pool_list").getAsJsonArray();
                sb.append("\n当前武器池：");
                for (JsonElement equip_card_pool : equip_card_pool_list) {
                    JsonObject equip_card_pool_object = equip_card_pool.getAsJsonObject();
                    String name = equip_card_pool_object.get("name").getAsString();
                    String start_time = equip_card_pool_object.getAsJsonObject("time_info").get("start_time").getAsString();
                    String end_time = equip_card_pool_object.getAsJsonObject("time_info").get("end_time").getAsString();
                    sb.append(name).append("\n 持续时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
                    JsonArray equip_list = equip_card_pool_object.get("equip_list").getAsJsonArray();
                    ArrayList<String> equip5_name = new ArrayList<>();
                    ArrayList<String> equip4_name = new ArrayList<>();
                    for (JsonElement avatar : equip_list) {
                        JsonObject avatar_object = avatar.getAsJsonObject();
                        String rarity = avatar_object.get("rarity").getAsString();
                        String avatar_name = avatar_object.get("item_name").getAsString();
                        if (rarity.equals("5")) equip5_name.add(avatar_name);
                        else equip4_name.add(avatar_name);
                    }
                    if (!equip5_name.isEmpty()) {
                        sb.append("  5⭐武器：");
                        for (String avatar_name : equip5_name)
                            sb.append(avatar_name).append("  ");
                    }
                    sb.append("\n");
                    if (!equip4_name.isEmpty()) {
                        sb.append("  4⭐武器：");
                        for (String avatar_name : equip4_name)
                            sb.append(avatar_name).append("  ");
                    }
                    sb.append("\n");
                }
                break;
            }
            return sb.toString();
        }

        /**
         * 返回活动日历小组件的json数据(好问题不能多用户)，可以做calendar的下位替代，可能可以做day2_widget的下位替代
         * 包含：开拓力，派遣，每日实训，模拟宇宙,活动及奖励等（比day2多了活动及奖励，少了派遣）
         *
         * @return String
         */
        public static String calendar2_widget() {
            if (no_has_role("崩坏：星穹铁道"))
                return ("当前账号无角色");
            Map<String, String> widget_headers = new HashMap<>(fixed.widget_headers);
            widget_headers.put("DS", getDS2("", SALT_6X, ""));
            widget_headers.put("cookie", "stuid=" + tools.files.read().get("stuid") + ";stoken=" + tools.files.read().get("stoken") + ";mid=" + tools.files.read().get("mid") + ";");
            String response = sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/app/hkrpg/aapi/get_act_calender_widget", widget_headers, null);//大爷的一个ar一个er
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            if (res.get("message").getAsString().equals("OK")) {
                return response;
            }
            throw new RuntimeException("calendar2获取出错" + response);
        }

        /**
         * 返回活动日历小组件的文本数据，可以做calendar的下位替代，可能可以做day2_widget的下位替代
         * 包含：开拓力，派遣，每日实训，模拟宇宙,活动及奖励等（比day2多了活动及奖励，少了派遣）
         *
         * @return String
         */
        public static String calendar2_widget_analysed() {
            String response = calendar2_widget();
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            JsonObject data = res.get("data").getAsJsonObject();
            JsonArray activities = data.get("activities").getAsJsonArray();
            for (JsonElement activity : activities) {
                JsonObject activity_object = activity.getAsJsonObject();
                String name = activity_object.get("name").getAsString();
                String start_time = activity_object.getAsJsonObject("time_info").get("start_time").getAsString();
                String end_time = activity_object.getAsJsonObject("time_info").get("end_time").getAsString();
                String panel_desc = activity_object.get("panel_desc").getAsString();
                int total_progress = activity_object.get("total_progress").getAsInt();
                int current_progress = activity_object.get("current_progress").getAsInt();
                sb.append(name).append("  ").append(current_progress).append("/").append(total_progress);
                if (!Objects.equals(panel_desc, "")) sb.append("\n  ").append(panel_desc);
                sb.append("\n  活动时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
                JsonArray reward_list = activity_object.get("reward_list").getAsJsonArray();
                sb.append("  活动奖励：").append("\n");
                for (JsonElement reward : reward_list) {
                    JsonObject reward_object = reward.getAsJsonObject();
                    String reward_name = reward_object.get("name").getAsString();
                    String reward_num = reward_object.get("num").getAsString();
                    sb.append("    ").append(reward_name).append(" x ").append(reward_num);
                }
                sb.append("\n");
            }
            JsonArray challenges = data.get("challenges").getAsJsonArray();
            sb.append("\n当前挑战：");
            for (JsonElement challenge : challenges) {
                JsonObject challenge_object = challenge.getAsJsonObject();
                String name = challenge_object.get("name_mi18n").getAsString();
                String start_time = challenge_object.getAsJsonObject("time_info").get("start_time").getAsString();
                String end_time = challenge_object.getAsJsonObject("time_info").get("end_time").getAsString();
                int total_progress = challenge_object.get("total_progress").getAsInt();
                int current_progress = challenge_object.get("current_progress").getAsInt();
                sb.append(name).append("  ").append(current_progress).append("/").append(total_progress)
                        .append("\n挑战时间：").append(start_time).append(" ~ ").append(end_time).append("\n");
                JsonArray reward_list = challenge_object.get("reward_list").getAsJsonArray();
                sb.append("挑战奖励：").append("\n");
                if (reward_list.isEmpty()) sb.delete(sb.length() - 6, sb.length());
                for (JsonElement reward : reward_list) {
                    JsonObject reward_object = reward.getAsJsonObject();
                    String reward_name = reward_object.get("name").getAsString();
                    String reward_num = reward_object.get("num").getAsString();
                    sb.append("  ").append(reward_name).append(" x ").append(reward_num);
                }
                sb.append("\n");
            }
            boolean is_tourn_unlocked = data.getAsJsonObject("rogue_info").get("tourn_unlocked").getAsBoolean();
            String tourn_current_score = data.getAsJsonObject("rogue_info").get("current_score").getAsString();
            String tourn_max_score = data.getAsJsonObject("rogue_info").get("max_score").getAsString();
            int current_stamina = data.getAsJsonObject("base_info").get("current_stamina").getAsInt();
            int max_stamina = data.getAsJsonObject("base_info").get("max_stamina").getAsInt();
            int stamina_recover_time = data.getAsJsonObject("base_info").get("stamina_recover_time").getAsInt();
            int current_reserve_stamina = data.getAsJsonObject("base_info").get("current_reserve_stamina").getAsInt();
            int current_train_score = data.getAsJsonObject("base_info").get("current_train_score").getAsInt();
            int max_train_score = data.getAsJsonObject("base_info").get("max_train_score").getAsInt();
            return "当前开拓力： " + current_stamina + "/" + max_stamina + "\n" +
                    "开拓力恢复时间：" + seconds_to_time(stamina_recover_time) + "\n" +
                    "每日实训： " + current_train_score + "/" + max_train_score + "\n" +
                    "当前储备开拓力：" + current_reserve_stamina + (current_reserve_stamina == 2400 ? " 储备已满" : "") + "\n" +
                    "模拟宇宙积分：" + (is_tourn_unlocked ? tourn_current_score + "/" + tourn_max_score : "未解锁") + "\n"
                    + "\n活动：\n" + sb;

        }

        /**
         * 月度收入信息（json源文件）
         *
         * @param month 格式为年月（202501） -只能查最多前两个月
         * @return String的List-防止多用户
         */
        public static List<String> month_info(int month) {
            if (no_has_role("崩坏：星穹铁道"))
                return List.of("当前账号无角色");
            List<Map<String, String>> hkrpgs = tools.files.read(name_to_game_id("星铁"));
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            List<String> responses = new ArrayList<>();
            for (Map<String, String> hkrpg : hkrpgs) {
                Map<String, String> params = new HashMap<>() {{
                    put("uid", hkrpg.get("game_uid"));
                    put("region", hkrpg.get("region"));
                    put("month", String.valueOf(month));
                }};
                String response = sendGetRequest("https://api-takumi.mihoyo.com/event/srledger/month_info", record_headers, params);
                responses.add(response);
            }
            return responses;
        }

        /**
         * 月度收入信息（简要分析后）
         *
         * @param month 格式为年月（202501） -只能查最多前两个月
         * @return String
         */
        public static String month_info_analysed(int month) {
            List<String> responses = month_info(month);
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject data = JsonParser.parseString(response).getAsJsonObject().get("data").getAsJsonObject();
                long uid = data.get("uid").getAsLong();
                String nickname = get_nickname(name_to_game_id("星铁"), uid);
                int current_month = data.get("month").getAsInt();
                int data_month = data.get("data_month").getAsInt();
                sb.append("崩坏：星穹铁道 : UID ").append(uid).append(" 开拓者：").append(nickname).append("\n")
                        .append("当前月份：").append(current_month).append("月 ; 查询月份：").append(data_month).append("月\n");
                if (data_month == current_month) {
                    JsonObject day_data = data.getAsJsonObject("day_data");
                    int current_hcoin = day_data.get("current_hcoin").getAsInt();
                    int current_rails_pass = day_data.get("current_rails_pass").getAsInt();
                    int last_hcoin = day_data.get("last_hcoin").getAsInt();
                    int last_rails_pass = day_data.get("last_rails_pass").getAsInt();
                    sb.append("今日星琼收入：").append(current_hcoin).append("  昨日星琼收入：").append(last_hcoin).append("  增长度 ")
                            .append(new BigDecimal(current_hcoin - last_hcoin).multiply(new BigDecimal(100)).divide(new BigDecimal(last_hcoin), 2, RoundingMode.HALF_UP)).append("%\n")
                            .append("今日通票&专票收入：").append(current_rails_pass).append("  昨日通票&专票收入：").append(last_rails_pass).append("  增长度 ")
                            .append(new BigDecimal(current_rails_pass - last_rails_pass).multiply(new BigDecimal(100)).divide(new BigDecimal(last_rails_pass), 2, RoundingMode.HALF_UP)).append("%\n");
                    sb.append("\n");
                }
                JsonObject month_data = data.getAsJsonObject("month_data");
                int current_hcoin = month_data.get("current_hcoin").getAsInt();
                int current_rails_pass = month_data.get("current_rails_pass").getAsInt();
                int last_hcoin = month_data.get("last_hcoin").getAsInt();
                int last_rails_pass = month_data.get("last_rails_pass").getAsInt();
                sb.append(data_month).append("月星琼收入：").append(current_hcoin).append("  上月星琼收入：").append(last_hcoin).append("  增长度 ")
                        .append(new BigDecimal(current_hcoin - last_hcoin).multiply(new BigDecimal(100)).divide(new BigDecimal(last_hcoin), 2, RoundingMode.HALF_UP)).append("%\n")
                        .append(data_month).append("月通票&专票收入：").append(current_rails_pass).append("  上月通票&专票收入：").append(last_rails_pass).append("  增长度 ")
                        .append(new BigDecimal(current_rails_pass - last_rails_pass).multiply(new BigDecimal(100)).divide(new BigDecimal(last_rails_pass), 2, RoundingMode.HALF_UP)).append("%\n");
                sb.append("\n星琼收入来源：\n");
                for (JsonElement group : month_data.getAsJsonArray("group_by")) {
                    JsonObject group_data = group.getAsJsonObject();
                    sb.append(group_data.get("action_name").getAsString()).append(" 收入：").append(group_data.get("num").getAsInt())
                            .append(" 收入占比：").append(group_data.get("percent").getAsInt()).append("%\n");
                }
            }
            return sb.toString();
        }
    }

    public static class ZZZ {
        /**
         * 获取每日信息（原始json String的List-防止多用户），
         * 包含：当前电量/电量恢复时间，已完成委托/委托总数，刮刮卡，每日经营等
         *
         * @return String
         */
        public static List<String> day() {
            if (no_has_role("绝区零"))
                return List.of("当前账号无角色");
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> zzz : zzzs) {
                String response = getResponse(zzz, "https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/note");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/note", "绝区零");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        public static String day_analysed() {
            List<String> responses = day();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            for (String response : responses) {
                String uid = zzzs.get(responses.indexOf(response)).get("game_uid");
                String nickname = zzzs.get(responses.indexOf(response)).get("nickname");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.get("data").getAsJsonObject();
                JsonObject energy = data.getAsJsonObject("energy");
                int current_energy = energy.getAsJsonObject("progress").get("current").getAsInt();
                int max_energy = energy.getAsJsonObject("progress").get("max").getAsInt();
                int energy_recover_time = energy.get("restore").getAsInt();
                int vitality_current = data.getAsJsonObject("vitality").get("current").getAsInt();
                int vitality_max = data.getAsJsonObject("vitality").get("max").getAsInt();
                int weekly_task_current_point = data.getAsJsonObject("weekly_task").get("cur_point").getAsInt();
                int weekly_task_max_point = data.getAsJsonObject("weekly_task").get("max_point").getAsInt();
                int bounty_commission_num = data.getAsJsonObject("bounty_commission").get("num").getAsInt();
                int bounty_commission_total = data.getAsJsonObject("bounty_commission").get("total").getAsInt();
                JsonElement survey_points = data.get("survey_points");
                JsonElement coffee = data.get("coffee");
                sb.append("绳匠：").append(nickname).append(" UID ").append(uid).append("\n")
                        .append("当前电量： ").append(current_energy).append("/").append(max_energy).append("\n")
                        .append("电量恢复时间：").append(seconds_to_time(energy_recover_time)).append("\n")
                        .append("储存电量： ").append(vitality_current).append("/").append(vitality_max).append("\n")
                        .append("刮刮卡：").append(survey_points.isJsonNull() ? "未完成" : "已完成").append("\n")
                        .append("咖啡店：").append(coffee.isJsonNull() ? "未完成" : "已完成").append("\n")
                        .append("悬赏委托进度：").append(bounty_commission_num).append("/").append(bounty_commission_total).append("\n")
                        .append("丽都周纪积分：").append(weekly_task_current_point).append("/").append(weekly_task_max_point);
            }
            return sb.toString();
        }

        /**
         * 危局强袭战数据（json源文件）
         *
         * @param month 1当前2上期
         * @return String的List-防止多用户
         */
        public static List<String> mem(int month) {
            if (no_has_role("绝区零"))
                return List.of("当前账号无角色");
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            record_headers.put("x-rpc-platform", "2");
            List<String> responses = new ArrayList<>();
            for (Map<String, String> zzz : zzzs) {
                Map<String, String> params = new HashMap<>() {{
                    put("uid", zzz.get("game_uid"));
                    put("region", zzz.get("region"));
                    put("schedule_type", String.valueOf(month));
                }};
                String response = sendGetRequest("https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/mem_detail", record_headers, params);
                responses.add(response);
            }
            return responses;
        }

        /**
         * 式舆防卫战的数据（json源文件）
         *
         * @param month 1当前2上期
         * @return String的List-防止多用户
         */

        public static List<String> challenge(int month) {
            if (no_has_role("绝区零"))
                return List.of("当前账号无角色");
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            record_headers.put("x-rpc-platform", "2");
            List<String> responses = new ArrayList<>();
            for (Map<String, String> zzz : zzzs) {
                Map<String, String> params = new HashMap<>() {{
                    put("role_id", zzz.get("game_uid"));
                    put("server", zzz.get("region"));
                    put("schedule_type", String.valueOf(month));
                }};
                String response = sendGetRequest("https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/challenge", record_headers, params);
                responses.add(response);
            }
            return responses;
        }

        /**
         * 零号空洞数据（json源文件）
         *
         * @param type 1迷失之地，2枯萎之都
         * @return String的List-防止多用户
         */
        public static List<String> abyss(int type) {
            if (no_has_role("绝区零"))
                return List.of("当前账号无角色");
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            record_headers.put("x-rpc-platform", "2");
            List<String> responses = new ArrayList<>();
            for (Map<String, String> zzz : zzzs) {
                Map<String, String> params = new HashMap<>() {{
                    put("role_id", zzz.get("game_uid"));
                    put("server", zzz.get("region"));
                }};
                String response;
                if (type == 1)
                    response = sendGetRequest("https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/abyss_abstract", record_headers, params);
                else
                    response = sendGetRequest("https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/abysss2_abstract", record_headers, params);
                responses.add(response);
            }
            return responses;
        }

        /**
         * 月度收入信息（json源文件）
         *
         * @param month 格式为年月（202501） -只能查最多前两个月
         * @return String的List-防止多用户
         */
        public static List<String> month_info(int month) {
            if (no_has_role("绝区零"))
                return List.of("当前账号无角色");
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            Map<String, String> record_headers = new HashMap<>(fixed.record_headers);
            List<String> responses = new ArrayList<>();
            for (Map<String, String> zzz : zzzs) {
                Map<String, String> params = new HashMap<>() {{
                    put("uid", zzz.get("game_uid"));
                    put("region", zzz.get("region"));
                    put("month", String.valueOf(month));
                }};
                String response = sendGetRequest("https://api-takumi.mihoyo.com/event/nap_ledger/month_info", record_headers, params);
                responses.add(response);
            }
            return responses;
        }

        /**
         * 月度收入信息（简要分析后）
         *
         * @param month 格式为年月（202501） -只能查最多前两个月
         * @return String的List-防止多用户
         */
        public static String month_info_analysed(int month) {
            List<String> responses = month_info(month);
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject data = JsonParser.parseString(response).getAsJsonObject().get("data").getAsJsonObject();
                long uid = data.get("uid").getAsLong();
                String nickname = data.getAsJsonObject("role_info").get("nickname").getAsString();
                int current_month = data.get("current_month").getAsInt();
                int data_month = data.get("data_month").getAsInt();
                sb.append("绝区零 : UID ").append(uid).append(" 绳匠：").append(nickname).append("\n")
                        .append("当前月份：").append(current_month).append("月 ; 查询月份：").append(data_month).append("月\n");
                sb.append(data_month).append(" 月 收入：").append("\n");
                JsonArray month_data = data.getAsJsonObject("month_data").getAsJsonArray("list");
                for (JsonElement month_data_element : month_data) {
                    JsonObject month_data_jsonObject = month_data_element.getAsJsonObject();
                    String data_name = month_data_jsonObject.get("data_name").getAsString();
                    int count = month_data_jsonObject.get("count").getAsInt();
                    sb.append(data_name).append(" x ").append(count).append("  ");
                }
                sb.append("\n菲林收入来源：\n");
                for (JsonElement group : data.getAsJsonObject("month_data").getAsJsonArray("income_components")) {
                    JsonObject group_data = group.getAsJsonObject();
                    sb.append(switch (group_data.get("action").getAsString()) {
                                case "daily_activity_rewards" -> "日常活跃奖励";
                                case "event_rewards" -> "活动奖励";
                                case "shiyu_rewards" -> "式舆防卫战&危局强袭站奖励";
                                case "mail_rewards" -> "邮件奖励";
                                case "hollow_rewards" -> "零号空洞奖励";
                                case "growth_rewards" -> "成长奖励";
                                case "other_rewards" -> "其他奖励";
                                default -> "未知来源";
                            }).append(" 收入：").append(group_data.get("num").getAsInt())
                            .append(" 收入占比：").append(group_data.get("percent").getAsInt()).append("%\n");
                }
            }
            return sb.toString();
        }


        /**
         * 获取世界信息（原始json String的List-防止多用户），
         * 包含：收集进度，深渊，代理人/邦布数，成就数等
         *
         * @return String
         */
        public static List<String> world() {
            if (no_has_role("绝区零"))
                return List.of("当前账号无角色");
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> zzz : zzzs) {
                String response = getResponse(zzz, "https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/index");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/index", "绝区零");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取世界信息（解析后的String），
         * 包含：收集进度，深渊，代理人/邦布数，成就数等
         *
         * @return String
         */
        public static String world_analysed() {
            List<String> responses = world();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> zzzs = tools.files.read(name_to_game_id("绝区零"));
            for (String response : responses) {
                String uid = zzzs.get(responses.indexOf(response)).get("game_uid");
                String nickname = zzzs.get(responses.indexOf(response)).get("nickname");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                JsonObject stats = data.getAsJsonObject("stats");
                int active_days = stats.get("active_days").getAsInt();
                int avatar_num = stats.get("avatar_num").getAsInt();
                String world_level_name = stats.get("world_level_name").getAsString();
                int buddy_num = stats.get("buddy_num").getAsInt();
                int achievement_count = stats.get("achievement_count").getAsInt();
                int climbing_tower_layer = stats.get("climbing_tower_layer").getAsInt();
                String memory_battlefield_rank_percent = stats.getAsJsonObject("memory_battlefield").get("rank_percent").getAsInt() / 100 + "%";
                int memory_battlefield_total_score = stats.getAsJsonObject("memory_battlefield").get("total_score").getAsInt();
                int memory_battlefield_total_star = stats.getAsJsonObject("memory_battlefield").get("total_star").getAsInt();
                int stable_zone_layer_count = stats.get("stable_zone_layer_count").getAsInt();
                int all_change_zone_layer_count = stats.get("all_change_zone_layer_count").getAsInt();
                int climbing_tower_s2_climbing_tower_layer = stats.getAsJsonObject("climbing_tower_s2").get("climbing_tower_layer").getAsInt();
                int climbing_tower_s2_floor_mvp_num = stats.getAsJsonObject("climbing_tower_s2").get("floor_mvp_num").getAsInt();
                sb.append(world_level_name).append("：").append(nickname).append(" UID ").append(uid).append("\n")
                        .append("  活跃天数：").append(active_days).append(" 成就数：").append(achievement_count).append("\n")
                        .append("  代理人数：").append(avatar_num).append(" 邦布数：").append(buddy_num).append("\n")
                        .append("  拟真鏖战试炼：").append(climbing_tower_layer).append(" 鏖战试炼：末路：").append(climbing_tower_s2_climbing_tower_layer)
                        .append(" 魔王奖章-").append(climbing_tower_s2_floor_mvp_num).append("\n")
                        .append("  危局强袭战：").append(memory_battlefield_total_score).append(" - ").append(memory_battlefield_rank_percent)
                        .append(" - ").append(memory_battlefield_total_star).append("星\n")
                        .append("  示舆防卫战：第").append(all_change_zone_layer_count).append("防线 - 稳定节点：").append(stable_zone_layer_count).append("\n");
                JsonArray commemorative_coins_list = stats.getAsJsonArray("commemorative_coins_list");
                sb.append("调查协会纪念币：").append("\n");
                for (JsonElement coin : commemorative_coins_list) {
                    JsonObject coin_obj = coin.getAsJsonObject();
                    sb.append("  ").append(coin_obj.get("name").getAsString()).append(" :").append(coin_obj.get("num").getAsInt()).append("\n");
                }
                JsonArray cat_notes_list = data.getAsJsonArray("cat_notes_list");
                sb.append("猫吉长官笔记：").append("\n");
                for (JsonElement cat_note : cat_notes_list) {
                    JsonObject cat_note_obj = cat_note.getAsJsonObject();
                    String cat_note_name = cat_note_obj.get("name").getAsString();
                    int cat_note_num = cat_note_obj.get("num").getAsInt();
                    int cat_note_total = cat_note_obj.get("total").getAsInt();
                    sb.append("  ").append(cat_note_name).append(" :").append(cat_note_num).append("/").append(cat_note_total)
                            .append((cat_note_num == cat_note_total ? "已集齐" : "未集齐")).append("\n");
                    JsonArray medal_list = cat_note_obj.getAsJsonArray("medal_list");
                    for (JsonElement medal : medal_list) {
                        JsonObject medal_obj = medal.getAsJsonObject();
                        boolean is_finish = medal_obj.get("is_finish").getAsBoolean();
                        String medal_name = medal_obj.get("name").getAsString();
                        String medal_desc = medal_obj.get("desc").getAsString();
                        sb.append("    ").append(is_finish ? "√" : "×").append(medal_name).append(" :").append(medal_desc).append("\n");
                    }
                }
                JsonArray all_medal_list = data.getAsJsonObject("game_data_show").getAsJsonArray("all_medal_list");
                sb.append("奖章：").append("\n");
                for (JsonElement medal : all_medal_list) {
                    JsonObject medal_obj = medal.getAsJsonObject();
                    String medal_name = medal_obj.get("name").getAsString();
                    String medal_num = medal_obj.get("number").getAsString();
                    sb.append("  ").append(medal_name).append(" --").append(medal_num).append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 返回桌面小组件的json数据(好问题不能多用户) -可用于day的下位替代
         * 包含：当前电量，每日奖励，刮刮卡，每日经营等等
         *
         * @return String
         */
        public static String day2_widget() {
            if (no_has_role("绝区零"))
                return ("当前账号无角色");
            Map<String, String> widget_headers = new HashMap<>(fixed.widget_headers);
            widget_headers.put("cookie", "stuid=" + tools.files.read().get("stuid") + ";stoken=" + tools.files.read().get("stoken") + ";mid=" + tools.files.read().get("mid") + ";");
            String response = sendGetRequest("https://api-takumi-record.mihoyo.com/event/game_record_zzz/api/zzz/widget", widget_headers, null);
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            if (res.get("message").getAsString().equals("OK")) {
                return response;
            }
            throw new RuntimeException("day2获取出错" + response);
        }

        /**
         * 返回桌面小组件的文本数据 -可用于day_analysed的下位替代
         * 包含：当前电量，每日奖励，刮刮卡，每日经营等等
         *
         * @return String
         */
        public static String day2_widget_analysed() {
            String response = day2_widget();
            JsonObject res = JsonParser.parseString(response).getAsJsonObject();
            JsonObject data = res.get("data").getAsJsonObject();
            int energy_max = data.get("energy").getAsJsonObject().get("progress").getAsJsonObject().get("max").getAsInt();
            int energy_current = data.get("energy").getAsJsonObject().get("progress").getAsJsonObject().get("current").getAsInt();
            int energy_restore = data.get("energy").getAsJsonObject().get("restore").getAsInt();
            int vitality_max = data.get("vitality").getAsJsonObject().get("max").getAsInt();
            int vitality_current = data.get("vitality").getAsJsonObject().get("current").getAsInt();
            JsonArray note_list = data.getAsJsonArray("note_list");
            StringBuilder sb = new StringBuilder();
            sb.append("当前电量: ").append(energy_current).append("/").append(energy_max).append("  恢复时间:").append(seconds_to_time(energy_restore)).append("\n");
            sb.append("每日奖励: ").append(vitality_current).append("/").append(vitality_max).append("\n");
            for (JsonElement note : note_list) {
                JsonObject note_object = note.getAsJsonObject();
                sb.append(note_object.get("name").getAsString()).append(": ").append(note_object.get("value").getAsString()).append("\n");
            }
            return sb.toString();
        }
    }

    public static class Honkai3 {
        /**
         * 获取月度收入信息（json源文件）
         *
         * @param month 1为本月2为上月
         * @return String的List-防止多用户
         */
        public static List<String> month_info(int month) {
            if (no_has_role("崩坏3"))
                return List.of("当前账号无角色");
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> bh3 : bh3s) {
                Map<String, String> params = new HashMap<>() {{
                    put("bind_uid", bh3.get("game_uid"));
                    put("bind_region", bh3.get("region"));
                    put("game_biz", name_to_game_id("崩坏3"));
                }};
                String url1, url2;
                if (month == 1) {
                    url1 = "https://api.mihoyo.com/bh3-weekly_finance/api/index";
                    url2 = "https://api-takumi.mihoyo.com/event/handbook/current_month_count";
                } else {
                    url1 = "https://api.mihoyo.com/bh3-weekly_finance/api/getLastMonthInfo";
                    url2 = "https://api-takumi.mihoyo.com/event/handbook/last_month_count";
                }
                String response = sendGetRequest(url1, record_headers, params);
                String response2 = sendGetRequest(url2, record_headers, params);
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, url1, "崩坏3");
                JsonObject jsonObject2 = JsonParser.parseString(response2).getAsJsonObject();
                jsonObject2 = captcha(jsonObject2, url2, "崩坏3");
                if (jsonObject.get("message").getAsString().equals("OK") && jsonObject2.get("message").getAsString().equals("OK")) {
                    jsonObject.getAsJsonObject("data").addProperty("count", jsonObject2.getAsJsonObject("data").get("count").getAsString());
                    responses.add(jsonObject.toString());
                }
            }
            return responses;
        }

        /**
         * 获取月度收入信息（简要分析后）
         *
         * @param month 1为本月2为上月
         * @return String
         */
        public static String month_info_analysed(int month) {
            List<String> responses = month_info(month);
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject data = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("data");
                long uid = data.get("uid").getAsLong();
                String nickname = get_nickname(fixed.name_to_game_id("崩坏3"), uid);
                int month_ = data.get("month").getAsInt();
                int month_hcoin = data.get("month_hcoin").getAsInt();
                int month_star = data.get("month_star").getAsInt();
                String count = data.get("count").getAsString();
                sb.append("舰长 ").append(nickname).append(" UID ").append(uid).append(" ").append(month_).append("月收入：\n")
                        .append("星石 ").append(month_hcoin).append("  水晶 ").append(month_star).append("  角色&武器补给卡 ").append(count).append(" 张\n");
                if (month == 1) {
                    int day_hcoin = data.get("day_hcoin").getAsInt();
                    int day_star = data.get("day_star").getAsInt();
                    int last_hcoin = data.get("last_hcoin").getAsInt();
                    int last_star = data.get("last_star").getAsInt();
                    sb.append("本日收入：\n")
                            .append("星石 ").append(day_hcoin).append("  水晶 ").append(day_star).append("\n")
                            .append("昨日收入：\n")
                            .append("星石 ").append(last_hcoin).append("  水晶 ").append(last_star).append("\n");
                } else if (month == 2) {
                    int last_month_hcoin = data.get("last_month_hcoin").getAsInt();
                    int last_month_star = data.get("last_month_star").getAsInt();
                    int star_rate = data.get("star_rate").getAsInt();
                    int hcoin_rate = data.get("hcoin_rate").getAsInt();
                    sb.append("上月收入：\n")
                            .append("星石 ").append(last_month_hcoin).append("  水晶 ").append(last_month_star).append("\n")
                            .append("月收入变化：\n")
                            .append("星石 ").append(star_rate).append("%  水晶 ").append(hcoin_rate).append("%\n");
                    JsonArray group_by = data.getAsJsonArray("group_by");
                    sb.append("\n上月水晶收入来源与占比：\n");
                    for (JsonElement group : group_by) {
                        JsonObject group_object = group.getAsJsonObject();
                        String name = group_object.get("name").getAsString();
                        int num = group_object.get("num").getAsInt();
                        int percent = group_object.get("percent").getAsInt();
                        sb.append(name).append("： ").append(num).append("  - ").append(percent).append("%\n");
                    }
                }
            }
            return sb.toString();
        }

        /**
         * 获取每日信息（原始json String的List-防止多用户），包含：当前体力/体力恢复时间，已完成委托/委托总数，战场等的简要数据
         *
         * @return String
         */
        public static List<String> day() {
            if (no_has_role("崩坏3"))
                return List.of("当前账号无角色");
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> bh3 : bh3s) {
                String response = getResponse(bh3, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/note");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/note", "崩坏3");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取每日信息（文本 String），包含：当前体力/体力恢复时间，已完成委托/委托总数，战场等的简要数据
         *
         * @return String
         */
        public static String day_analysed() {
            List<String> reses = day();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            for (String res : reses) {
                String name = bh3s.get(reses.indexOf(res)).get("nickname");
                String game_uid = bh3s.get(reses.indexOf(res)).get("game_uid");
                JsonObject jsonObject = JsonParser.parseString(res).getAsJsonObject();
                JsonObject data = jsonObject.getAsJsonObject("data");
                int current_stamina = data.get("current_stamina").getAsInt();
                int max_stamina = data.get("max_stamina").getAsInt();
                int stamina_recover_time = data.get("stamina_recover_time").getAsInt();
                int current_train_score = data.get("current_train_score").getAsInt();
                int max_train_score = data.get("max_train_score").getAsInt();
                long greedy_endless_schedule_end = Long.parseLong(data.getAsJsonObject("greedy_endless").get("schedule_end").getAsString()) * 1000L;
                long ultra_endless_schedule_end = Long.parseLong(data.getAsJsonObject("ultra_endless").get("schedule_end").getAsString()) * 1000L;
                long battle_field_schedule_end = Long.parseLong(data.getAsJsonObject("battle_field").get("schedule_end").getAsString()) * 1000L;
                long god_war_schedule_end = Long.parseLong(data.getAsJsonObject("god_war").get("schedule_end").getAsString()) * 1000L;
                String greedy_end = new SimpleDateFormat("MM月-dd日 HH:mm:ss").format(new Date(greedy_endless_schedule_end));
                int greedy_cur_reward = data.getAsJsonObject("greedy_endless").get("cur_reward").getAsInt();
                int greedy_max_reward = data.getAsJsonObject("greedy_endless").get("max_reward").getAsInt();
                boolean greedy_open = data.getAsJsonObject("greedy_endless").get("is_open").getAsBoolean();
                String ultra_end = new SimpleDateFormat("MM月-dd日 HH:mm:ss").format(new Date(ultra_endless_schedule_end));
                int ultra_group_level = data.getAsJsonObject("ultra_endless").get("group_level").getAsInt();
                int ultra_challenge_score = data.getAsJsonObject("ultra_endless").get("challenge_score").getAsInt();
                boolean ultra_open = data.getAsJsonObject("ultra_endless").get("is_open").getAsBoolean();
                String battle_field = new SimpleDateFormat("MM月-dd日 HH:mm:ss").format(battle_field_schedule_end);
                int battle_cur_reward = data.getAsJsonObject("battle_field").get("cur_reward").getAsInt();
                int battle_max_reward = data.getAsJsonObject("battle_field").get("max_reward").getAsInt();
                int battle_cur_sss_reward = data.getAsJsonObject("battle_field").get("cur_sss_reward").getAsInt();
                int battle_max_sss_reward = data.getAsJsonObject("battle_field").get("max_sss_reward").getAsInt();
                boolean battle_open = data.getAsJsonObject("battle_field").get("is_open").getAsBoolean();
                String god_war = new SimpleDateFormat("MM月-dd日 HH:mm:ss").format(god_war_schedule_end);
                int god_cur_reward = data.getAsJsonObject("god_war").get("cur_reward").getAsInt();
                int god_max_reward = data.getAsJsonObject("god_war").get("max_reward").getAsInt();
                boolean god_open = data.getAsJsonObject("god_war").get("is_open").getAsBoolean();
                sb.append("舰长 ").append(name).append(" UID ").append(game_uid).append("\n")
                        .append("当前体力: ").append(current_stamina).append("/").append(max_stamina).append("  恢复时间: ").append(seconds_to_time(stamina_recover_time)).append("\n")
                        .append("已完成委托: ").append(current_train_score).append("/").append(max_train_score).append(current_train_score > max_train_score ? "  已经完成所有委托" : "  还有委托未完成").append("\n\n")
                        .append("量子流形：\n").append(greedy_open ? " 可获得奖励：" + greedy_cur_reward + " | " + greedy_max_reward : " 暂未开启")
                        .append("\n 刷新时间: ").append(greedy_end).append("\n")
                        .append("超弦空间：\n").append(ultra_open ? " 扰动等级：" + ultra_group_level + "/最终分数" + ultra_challenge_score : " 暂未开启")
                        .append("\n 刷新时间: ").append(ultra_end).append("\n").append("记忆战场：\n")
                        .append(battle_open ? " 可获得奖励：" + battle_cur_reward + "/" + battle_max_reward + "  sss挑战数：" + battle_cur_sss_reward + "/" + battle_max_sss_reward : "暂未开启")
                        .append("\n 刷新时间: ").append(battle_field).append("\n").append("往事乐土：\n").append(god_open ? " 可获得奖励：" + god_cur_reward + "/" + god_max_reward : " 暂未开启")
                        .append("\n 刷新时间: ").append(god_war).append("\n");
            }
            return sb.toString();
        }

        /**
         * 获取崩坏3世界探索信息
         *
         * @return String -json 的List-防止多用户
         */
        public static List<String> world() {
            if (no_has_role("崩坏3"))
                return List.of("当前账号无角色");
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> bh3 : bh3s) {
                String response = getResponse(bh3, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/index");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/index", "崩坏3");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取纯文本的崩坏3世界探索信息
         *
         * @return String
         */
        public static String world_analysed() {
            List<String> responses = world();
            StringBuilder sb = new StringBuilder();
            for (String response : responses) {
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                String name = data.getAsJsonObject("role").get("nickname").getAsString();
                String level = data.getAsJsonObject("role").get("level").getAsString();
                String region = data.getAsJsonObject("role").get("region").getAsString();
                JsonObject stats = data.getAsJsonObject("stats");
                int active_day_number = stats.get("active_day_number").getAsInt();
                int suit_number = stats.get("suit_number").getAsInt();
                int achievement_number = stats.get("achievement_number").getAsInt();
                int stigmata_number = stats.get("stigmata_number").getAsInt();
                int armor_number = stats.get("armor_number").getAsInt();
                int sss_armor_number = stats.get("sss_armor_number").getAsInt();
                String battle_field_ranking_percentage = stats.get("battle_field_ranking_percentage").getAsString() + "%";
                int weapon_number = stats.get("weapon_number").getAsInt();
                int five_star_weapon_number = stats.get("five_star_weapon_number").getAsInt();
                int five_star_stigmata_number = stats.get("five_star_stigmata_number").getAsInt();
                int god_war_max_level_avatar_number = stats.get("god_war_max_level_avatar_number").getAsInt();
                int battle_field_area = stats.get("battle_field_area").getAsInt();
                int battle_field_score = stats.get("battle_field_score").getAsInt();
                int abyss_score = stats.get("abyss_score").getAsInt();
                int battle_field_rank = stats.get("battle_field_rank").getAsInt();
                int god_war_max_support_point = stats.get("god_war_max_support_point").getAsInt();
                int abyss_floor = stats.get("abyss_floor").getAsInt();
                int explore_score = stats.get("explore_score").getAsInt();
                int explore_score_sum = stats.get("explore_score_sum").getAsInt();
                JsonObject preference = data.getAsJsonObject("preference");
                int abyss = preference.get("abyss").getAsInt();
                int main_line = preference.get("main_line").getAsInt();
                int battle_field = preference.get("battle_field").getAsInt();
                int open_world = preference.get("open_world").getAsInt();
                int comprehensive_score = preference.get("comprehensive_score").getAsInt();
                String comprehensive_rating = preference.get("comprehensive_rating").getAsString();
                boolean is_god_war_unlock = preference.get("is_god_war_unlock").getAsBoolean();
                int god_war = preference.get("god_war").getAsInt();
                sb.append("舰长：").append(name).append(" 等级：").append(level).append(" 服务器：").append(region).append("\n")
                        .append("累计登舰天数").append(active_day_number).append("  服装数：").append(suit_number)
                        .append("  总成就数：").append(achievement_number).append("  \n")
                        .append("总圣痕数：").append(stigmata_number).append("  装甲数：").append(armor_number)
                        .append("  SSS装甲数：").append(sss_armor_number).append("  \n")
                        .append("记忆战场：").append(battle_field_ranking_percentage).append("  总武器数：").append(weapon_number)
                        .append("  5星武器数：").append(five_star_weapon_number).append("  \n")
                        .append("5星圣痕数：").append(five_star_stigmata_number).append("  乐土满级角色数：").append(god_war_max_level_avatar_number)
                        .append("  记忆战场区域：").append(battle_field_area).append("  \n")
                        .append("记忆战场分数：").append(battle_field_score).append("  深渊分数：").append(abyss_score)
                        .append("  记忆战场排名：").append(battle_field_rank).append("  \n")
                        .append("乐土最大点数：").append(god_war_max_support_point).append("  深渊层数：").append(abyss_floor)
                        .append("  探索分数：").append(explore_score).append("/").append(explore_score_sum).append("  \n")
                        .append("\n舰长评分：").append(comprehensive_score).append("  综合评级：").append(comprehensive_rating).append("\n")
                        .append("深渊 ").append(abyss).append("  主线 ").append(main_line).append("  记忆战场 ").append(battle_field)
                        .append("  开放世界 ").append(open_world).append(is_god_war_unlock ? "  乐土 " + god_war : "");
            }
            return sb.toString();
        }

        /**
         * 获取崩坏3周报
         *
         * @return String -json 的List-防止多用户
         */
        public static List<String> weekly_report() {
            if (no_has_role("崩坏3"))
                return List.of("当前账号无角色");
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> bh3 : bh3s) {
                String response = getResponse(bh3, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/weeklyReport");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/weeklyReport", "崩坏3");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取崩坏3周报，简要分析后
         *
         * @return String
         */
        public static String weekly_report_analysed() {
            List<String> responses = weekly_report();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            for (String response : responses) {
                String name = bh3s.get(responses.indexOf(response)).get("nickname");
                String game_uid = bh3s.get(responses.indexOf(response)).get("game_uid");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                String favorite_character = data.getAsJsonObject("favorite_character").get("name").getAsString();
                int gold_income = data.get("gold_income").getAsInt();
                int gold_expenditure = data.get("gold_expenditure").getAsInt();
                int active_day_number = data.get("active_day_number").getAsInt();
                int online_hours = data.get("online_hours").getAsInt();
                int expended_physical_power = data.get("expended_physical_power").getAsInt();
                int main_line_expended_physical_power_percentage = data.get("main_line_expended_physical_power_percentage").getAsInt();
                long time_from = data.get("time_from").getAsLong() * 1000L;
                long time_to = data.get("time_to").getAsLong() * 1000L;
                String from = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time_from));
                String to = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time_to));
                sb.append("舰长 ").append(name).append(" UID ").append(game_uid).append("\n")
                        .append("周报 (").append(from).append(" ~ ").append(to).append(")：\n")
                        .append("最爱角色：").append(favorite_character).append("\n")
                        .append("金币收入：").append(gold_income).append("  金币支出：").append(gold_expenditure).append("  \n")
                        .append("活跃天数：").append(active_day_number).append("  在线小时数：").append(online_hours).append("  \n")
                        .append("消耗体力：").append(expended_physical_power).append("  主线消耗率：").append(main_line_expended_physical_power_percentage).append("%");
            }
            return sb.toString();
        }

        /**
         * 获取崩坏3记忆战场战斗记录
         *
         * @return String -json 的List-防止多用户
         */
        public static List<String> battle_report() {
            if (no_has_role("崩坏3"))
                return List.of("当前账号无角色");
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> bh3 : bh3s) {
                String response = getResponse(bh3, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/battleFieldReport");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/battleFieldReport", "崩坏3");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取崩坏3记忆战场战斗记录，简要分析后
         *
         * @return String
         */
        public static String battle_report_analysed() {
            List<String> responses = battle_report();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            for (String response : responses) {
                String name = bh3s.get(responses.indexOf(response)).get("nickname");
                String game_uid = bh3s.get(responses.indexOf(response)).get("game_uid");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                JsonArray reports = data.getAsJsonArray("reports");
                sb.append("舰长 ").append(name).append(" UID ").append(game_uid).append("\n").append("记忆战场挑战：\n");
                for (JsonElement element : reports) {
                    JsonObject report = element.getAsJsonObject();
                    long time_second = Long.parseLong(report.get("time_second").getAsString()) * 1000L;
                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time_second));
                    int score = report.get("score").getAsInt();
                    int rank = report.get("rank").getAsInt();
                    String ranking_percentage = report.get("ranking_percentage").getAsString() + "%";
                    sb.append("挑战时间：").append(time).append("  总分 ").append(score).append("  排名 ")
                            .append(ranking_percentage).append("  等级区 ").append(rank).append("\n");
                    JsonArray battle_infos = report.getAsJsonArray("battle_infos");
                    for (JsonElement battle_info : battle_infos) {
                        JsonObject battle_info_obj = battle_info.getAsJsonObject();
                        int score1 = battle_info_obj.get("score").getAsInt();
                        String elf_name = battle_info_obj.get("elf").getAsJsonObject().get("name").getAsString();
                        if (elf_name.isEmpty()) elf_name = "第二部的助战";
                        String boss_name = battle_info_obj.get("boss").getAsJsonObject().get("name").getAsString();
                        sb.append("   boss：").append(boss_name).append("  挑战分数：").append(score1).append("\n    助战角色：").append(elf_name).append("  角色：");
                        JsonArray lineup = battle_info_obj.get("lineup").getAsJsonArray();
                        for (JsonElement ele : lineup) {
                            JsonObject avatar = ele.getAsJsonObject();
                            String avatar_name = avatar.get("name").getAsString();
                            sb.append(avatar_name).append("  ");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 获取崩坏3深渊战报
         *
         * @return String -json 的List-防止多用户
         */
        public static List<String> abyss_report() {
            if (no_has_role("崩坏3"))
                return List.of("当前账号无角色");
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            List<String> responses = new ArrayList<>();
            for (Map<String, String> bh3 : bh3s) {
                String response = getResponse(bh3, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/latestOldAbyssReport");
                JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                jsonObject = captcha(jsonObject, "https://api-takumi-record.mihoyo.com/game_record/app/honkai3rd/api/latestOldAbyssReport", "崩坏3");
                if (jsonObject.get("message").getAsString().equals("OK")) {
                    responses.add(response);
                }
            }
            return responses;
        }

        /**
         * 获取崩坏3深渊战报，简要分析后
         *
         * @return String
         */
        public static String abyss_report_analysed() {
            List<String> responses = abyss_report();
            StringBuilder sb = new StringBuilder();
            List<Map<String, String>> bh3s = tools.files.read(name_to_game_id("崩坏3"));
            for (String response : responses) {
                String name = bh3s.get(responses.indexOf(response)).get("nickname");
                String game_uid = bh3s.get(responses.indexOf(response)).get("game_uid");
                JsonObject res = JsonParser.parseString(response).getAsJsonObject();
                JsonObject data = res.getAsJsonObject("data");
                JsonArray reports = data.getAsJsonArray("reports");
                sb.append("舰长 ").append(name).append(" UID ").append(game_uid).append("\n").append("深渊挑战：\n");
                for (JsonElement element : reports) {
                    JsonObject report = element.getAsJsonObject();
                    long time_second = Long.parseLong(report.get("time_second").getAsString()) * 1000L;
                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time_second));
                    int score = report.get("score").getAsInt();
                    String level = report.get("level").getAsString();
                    String floor = report.get("floor").getAsString();
                    String elf_name = report.get("elf").getAsJsonObject().get("name").getAsString();
                    if (elf_name.isEmpty()) elf_name = "第二部的助战";
                    String boss_name = report.get("boss").getAsJsonObject().get("name").getAsString();
                    sb.append("挑战时间：").append(time).append("   boss：").append(boss_name).append("  总分 ").append(score).append("  层数 ").append(floor).append("  评级 ").append(level)
                            .append("\n    助战角色：").append(elf_name).append("  角色：");
                    JsonArray lineup = report.get("lineup").getAsJsonArray();
                    for (JsonElement ele : lineup) {
                        JsonObject avatar = ele.getAsJsonObject();
                        String avatar_name = avatar.get("name").getAsString();
                        sb.append(avatar_name).append("  ");
                    }
                    sb.append("\n\n");
                }
            }
            return sb.toString();
        }
    }
}

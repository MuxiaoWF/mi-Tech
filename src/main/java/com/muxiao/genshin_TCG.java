package com.muxiao;

import com.google.gson.Gson;

import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import static com.muxiao.fixed.genshin_TCG_headers;
import static com.muxiao.tools.sendGetRequest;
import static com.muxiao.tools.sendPostRequest;

/**
 * 执行原神七圣召唤任务
 */
public class genshin_TCG {

    private static final Gson gson = new Gson();
    private final Map<Integer, Map<String, Object>> taskList = new HashMap<>() {{
        put(101, new HashMap<>(Map.of("task_id", 101, "task_name", "每日签到", "finish", false, "reward", false)));
        put(503, new HashMap<>(Map.of("task_id", 503, "task_name", "每周完成冠胜之试", "finish", false, "reward", false)));
        put(504, new HashMap<>(Map.of("task_id", 504, "task_name", "每周完成10场匹配", "finish", false, "reward", false)));
        put(505, new HashMap<>(Map.of("task_id", 505, "task_name", "每周获得3场匹配胜利", "finish", false, "reward", false)));
    }};
    private final tools.StatusNotifier statusNotifier;
    private Map<String, String> userInfo;
    private Map<String, String> params;

    /**
     * 构造函数，初始化任务列表
     *
     * @param statusNotifier 状态通知器
     * @param checkInTask    是否执行每日签到任务
     * @param weekTask       是否执行每周任务
     */
    public genshin_TCG(tools.StatusNotifier statusNotifier, Boolean checkInTask, Boolean weekTask) {
        this.statusNotifier = statusNotifier;
        List<Map<String, String>> userListInfo = getHk4eToken(statusNotifier);
        for (Map<String, String> user : userListInfo) {
            this.userInfo = user;
            if (userInfo != null) {
                genshin_TCG_headers.put("x-rpc-cover-session", userInfo.get("game_uid"));
                params = Map.of("badge_uid", userInfo.get("game_uid"), "badge_region", userInfo.get("region"), "game_biz", userInfo.get("game_biz"), "lang", "zh-cn");
            }
            statusNotifier.notifyListeners(runTask(checkInTask, weekTask));
        }
    }

    /**
     * 从响应头中获取hk4e_token
     */
    private static String getHk4eToken(Map<String, String> headers, Map<String, Object> body) throws Exception {
        URL url = new URI("https://api-takumi.mihoyo.com/common/badge/v1/login/account").toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            con.setRequestProperty(entry.getKey(), entry.getValue());
        }
        con.setDoOutput(true);
        Gson gson = new Gson();
        String jsonInputString = gson.toJson(body);
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed : HTTP error code : " + responseCode);
        }
        // 读取响应头
        String cookieHeader = con.getHeaderField("Set-Cookie");
        if (cookieHeader == null) {
            throw new RuntimeException("Set-Cookie header not found in response");
        }
        Pattern pattern = Pattern.compile("e_hk4e_token=([^;]+);");
        Matcher matcher = pattern.matcher(cookieHeader);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("e_hk4e_token not found in Set-Cookie header");
        }
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息
     */
    private List<Map<String, String>> getHk4eToken(tools.StatusNotifier statusNotifier) {
        if (tools.files.read().get("e_hk4e_token") != null && tools.files.read().get(fixed.name_to_game_id("原神")+"_user") != null) {
            String cookie = "account_id=" + tools.files.read().get("stuid") + ";cookie_token=" + tools.files.read().get("cookie_token");
            genshin_TCG_headers.put("Cookie", cookie + ";e_hk4e_token=" + tools.files.read().get("e_hk4e_token"));
            return tools.files.read(fixed.name_to_game_id("原神"));
        }
        statusNotifier.notifyListeners("正在获取hk4e token");
        if (tools.files.read().get("cookie_token") == null) {
            fixed.getCookieTokenByStoken();
        }
        String cookie = "account_id=" + tools.files.read().get("stuid") + ";cookie_token=" + tools.files.read().get("cookie_token");
        List<Map<String, String>> accountList = bbs_daily.bbs_game_daily.Genshin.getAccountList(statusNotifier);
        List<Map<String, String>> userListInfo = new ArrayList<>();
        for (Map<String, String> account : accountList) {
            String region = account.get("region");
            String uid = account.get("game_uid");
            String nickname = account.get("nickname");
            Map<String, String> headers = new HashMap<>() {{
                put("Cookie", cookie);
                put("Content-Type", "application/json;charset=UTF-8");
                put("Referer", "https://webstatic.mihoyo.com/");
                put("Origin", "https://webstatic.mihoyo.com");
            }};
            Map<String, Object> body = Map.of(
                    "uid", uid,
                    "game_biz", fixed.name_to_game_id("原神"),
                    "lang", "zh-cn",
                    "region", "cn_gf01"
            );
            try {
                String e_hk4e_token = getHk4eToken(headers, body);
                tools.files.write("e_hk4e_token", e_hk4e_token);
                genshin_TCG_headers.put("Cookie", cookie + ";e_hk4e_token=" + e_hk4e_token);
            } catch (UnknownHostException e) {
                throw new RuntimeException("请检查是否联网");
            } catch (Exception e) {
                throw new RuntimeException("出错了" + e);
            }
            Map<String, String> user = Map.of("nickname", nickname, "game_uid", uid, "region", region, "game_biz", fixed.name_to_game_id("原神"));
            userListInfo.add(user);
        }
        return userListInfo;
    }

    /**
     * 获取当前登录状态
     */
    private boolean getStatus() {
        try {
            String response = sendGetRequest("https://hk4e-api.mihoyo.com/event/geniusinvokationtcg/rd_info", genshin_TCG_headers, params);
            JsonObject data = gson.fromJson(response, JsonObject.class);
            return data.get("retcode").getAsInt() != -521030;
        } catch (Exception e) {
            statusNotifier.notifyListeners("获取状态失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从网页中获取任务列表
     */
    private void getTaskList() {
        try {
            String response = sendGetRequest("https://hk4e-api.mihoyo.com/event/geniusinvokationtcg/adventure_task_list", genshin_TCG_headers, params);
            JsonObject data = gson.fromJson(response, JsonObject.class);
            if (data.get("retcode").getAsInt() != 0) {
                return;
            }
            for (var task : data.getAsJsonObject("data").getAsJsonArray("active_tasks")) {
                JsonObject taskObj = task.getAsJsonObject();
                int taskId = taskObj.get("task_id").getAsInt();
                Map<String, Object> taskInfo = taskList.get(taskId);
                if (taskInfo == null) {
                    continue;
                }
                if ("Reward".equals(taskObj.get("status").getAsString())) {
                    taskInfo.put("reward", true);
                } else if ("Finish".equals(taskObj.get("status").getAsString())) {
                    taskInfo.put("finish", true);
                }
            }
        } catch (Exception e) {
            statusNotifier.notifyListeners("获取任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取奖励链接
     *
     * @param taskId 任务id参数
     * @return 奖励链接
     */
    private boolean getAward(int taskId) {
        StringBuilder url = new StringBuilder("https://hk4e-api.mihoyo.com/event/geniusinvokationtcg/award_adventure_task");
        if (!params.isEmpty()) {
            url.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (url.length() > "https://hk4e-api.mihoyo.com/event/geniusinvokationtcg/award_adventure_task".length() + 1) {
                    url.append("&");
                }
                url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        try {
            String response = sendPostRequest(url.toString(), genshin_TCG_headers, Map.of("task_id", taskId));
            JsonObject data = gson.fromJson(response, JsonObject.class);
            int retcode = data.get("retcode").getAsInt();
            return retcode == 0 || retcode == -521040;
        } catch (Exception e) {
            statusNotifier.notifyListeners("领取奖励失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 每日签到
     *
     * @return 签到结果
     */
    private String checkin() {
        Map<String, Object> taskInfo = taskList.get(101);
        if ((boolean) taskInfo.get("reward")) {
            return "已经签到过了";
        }
        if (!(boolean) taskInfo.get("finish")) {
            return "无法获取每日签到奖励";
        }
        if (getAward((int) taskInfo.get("task_id"))) {
            taskInfo.put("reward", true);
            statusNotifier.notifyListeners("成功领取每日签到奖励");
            return "成功签到";
        }
        return "无法进行签到";
    }

    /**
     * 每周任务
     *
     * @param taskIds 任务id数组
     * @return 任务结果
     */
    private String weekTask(int[] taskIds) {
        StringBuilder results = new StringBuilder();
        for (int taskId : taskIds) {
            String result = doWeekTask(taskId);
            results.append(result).append("\n");
        }
        return results.toString();
    }

    /**
     * 尝试执行每个每周任务
     *
     * @param taskId 任务id
     * @return 任务结果
     */
    private String doWeekTask(int taskId) {
        Map<String, Object> taskInfo = taskList.get(taskId);
        if ((boolean) taskInfo.get("reward")) {
            return "";
        }
        if (!(boolean) taskInfo.get("finish")) {
            return "每周任务:" + taskInfo.get("task_name") + " 还未完成";
        }
        if (getAward((int) taskInfo.get("task_id"))) {
            taskInfo.put("reward", true);
            return "成功领取每周任务:" + taskInfo.get("task_name") + " 奖励";
        }
        return "无法领取 " + taskInfo.get("task_name") + " 奖励";
    }

    /**
     * 开始执行
     *
     * @param checkinTask 是否执行每日签到任务
     * @param weekTask    是否执行每周任务
     */
    private String runTask(Boolean checkinTask, Boolean weekTask) {
        try {
            Thread.sleep(new Random().nextInt(6) + 3);
            statusNotifier.notifyListeners("七圣召唤赛事任务开始");
            StringBuilder result = new StringBuilder("七圣召唤比赛: ");
            if (userInfo == null) {
                result.append("账号没有绑定任何原神账号！");
                return result.toString();
            }
            if (!getStatus()) {
                result.append("七圣赛事维护中");
                return result.toString();
            }
            getTaskList();
            Thread.sleep(new Random().nextInt(6) + 3);
            if (checkinTask) {
                result.append("\n").append(checkin());
                Thread.sleep(new Random().nextInt(6) + 3);
            }
            if (weekTask) {
                result.append("\n").append(weekTask(new int[]{503, 504, 505}));
            }
            statusNotifier.notifyListeners("七圣召唤赛事任务执行完毕");
            return result.toString();
        } catch (InterruptedException e) {
            return "任务执行中断" + e.getMessage();
        }
    }
}

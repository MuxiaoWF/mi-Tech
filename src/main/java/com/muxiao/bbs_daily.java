package com.muxiao;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;

import static com.muxiao.fixed.*;
import static com.muxiao.get_stoken_qrcode.getDS2;
import static com.muxiao.tools.sendGetRequest;
import static com.muxiao.tools.sendPostRequest;

public class bbs_daily {
    private final Map<String, Boolean> taskDo = new HashMap<>() {{
        put("sign", false);
        put("read", false);
        put("like", false);
        put("share", false);
    }};
    private final Map<String, Integer> taskTimes = new HashMap<>() {{
        put("read_num", 3);
        put("like_num", 5);
    }};
    private final List<Map<String, String>> this_bbsList;
    private final tools.StatusNotifier notifier;
    private int todayGetCoins;
    private int todayHaveGetCoins;
    private int haveCoins;
    private List<List<String>> postsList;

    /**
     * 米游社任务对象
     *
     * @param name     需要社区签到的板块名称（米游币的那个）可填：崩坏3、原神、崩坏2、未定事件簿、大别野、星铁、绝区零
     * @param stoken   stoken
     * @param mid      mid
     * @param cookie   cookie（只要stuid也行）
     * @param notifier 状态通知器
     */
    public bbs_daily(String[] name, String stoken, String mid, String cookie, tools.StatusNotifier notifier) {
        if (stoken == null) {
            stoken = tools.files.read().get("stoken");
            if (stoken == null)
                throw new RuntimeException("stoken为null,请尝试抓取stoken");
        }
        if (mid == null) {
            mid = tools.files.read().get("mid");
            if (mid == null)
                throw new RuntimeException("mid为null,请尝试抓取mid");
        }
        if (cookie == null) {
            cookie = "ltuid=" + tools.files.read().get("stuid");
            if (tools.files.read().get("stuid") == null)
                throw new RuntimeException("stuid为null,请尝试抓取包含stuid/ltuid的cookie");
        }
        tools.files.write("stoken", stoken);
        tools.files.write("mid", mid);
        this.notifier = notifier;
        this_bbsList = new ArrayList<>();
        for (String key : name) {
            for (Map<String, String> map : bbs_list) {
                if (key.equals(map.get("name"))) {
                    this_bbsList.add(map);
                    break;
                }
            }
        }
        cookie = tidyCookie(cookie);
        String stuid = getUid(cookie);
        bbs_headers.put("cookie", getStokenCookie(stoken, mid, stuid));
        getTasksList();
        this.postsList = this.getList();
    }

    /**
     * 米游社任务对象,从文件中读取stoken，mid，stuid
     *
     * @param name     需要社区签到的板块名称（米游币的那个）可填：崩坏3、原神、崩坏2、未定事件簿、大别野、星铁、绝区零
     * @param notifier 状态通知器
     */
    public bbs_daily(String[] name, tools.StatusNotifier notifier) {
        this.notifier = notifier;
        this_bbsList = new ArrayList<>();
        String stoken = tools.files.read().get("stoken");
        String mid = tools.files.read().get("mid");
        String cookie = "ltuid=" + tools.files.read().get("stuid");
        if (stoken == null || mid == null || tools.files.read().get("stuid") == null) {
            notifier.notifyListeners("未找到stoken,mid,stuid,请尝试重新抓取或使用另一个构造方法");
            throw new RuntimeException("未找到stoken,mid,stuid,请尝试重新抓取或使用另一个构造方法");
        }
        for (String key : name) {
            for (Map<String, String> map : bbs_list) {
                if (key.equals(map.get("name"))) {
                    this_bbsList.add(map);
                    break;
                }
            }
        }
        cookie = tidyCookie(cookie);
        String stuid = getUid(cookie);
        bbs_headers.put("cookie", getStokenCookie(stoken, mid, stuid));
        getTasksList();
        this.postsList = this.getList();
    }

    /**
     * 从网页的cookie中获取ltuid(stuid) -二者值一样
     *
     * @param cookie cookie
     * @return ltuid(stuid)
     */
    private static String getUid(String cookie) {
        Pattern pattern = Pattern.compile("(account_id|ltuid|login_uid|ltuid_v2|account_id_v2)=(\\d+)");
        Matcher matcher = pattern.matcher(cookie);
        if (!matcher.find()) {
            throw new RuntimeException("Cookie中未找到ltuid,请尝试重新抓取");
        }
        tools.files.write("stuid", matcher.group(2));
        return matcher.group(2);
    }

    /**
     * 将v1的stoken转成v2的stoken
     *
     * @param stuid  ltuid，可从getUid方法中获取
     * @param stoken stoken_v1
     * @param mid    mid
     * @return String[], [0] stoken_v2, [1] mid, [2] stuid
     */
    private static String[] v1tov2(String stuid, String stoken, String mid) {
        if (stoken.startsWith("v2_"))
            return new String[]{stoken, mid, stuid};
        String cookie = "stuid=" + stuid + ";stoken=" + stoken;
        if (mid != null && !mid.isEmpty()) {
            cookie += ";mid=" + mid;
        }
        get_token_by_stoken_headers.put("Cookie", cookie);
        String response = tools.sendPostRequest("https://passport-api.mihoyo.com/account/ma-cn-session/app/getTokenBySToken", get_token_by_stoken_headers, null);
        // 解析 JSON 响应
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
        int retcode = jsonObject.get("retcode").getAsInt();
        if (retcode == 0) {
            JsonObject data = jsonObject.getAsJsonObject("data");
            JsonObject token = data.getAsJsonObject("token");
            JsonObject userInfo = data.getAsJsonObject("user_info");
            String stokenV2 = token.get("token").getAsString();
            String midV2 = userInfo.get("mid").getAsString();
            tools.files.write("stoken", stokenV2);
            tools.files.write("mid", midV2);
            return new String[]{stokenV2, midV2, stuid};
        } else if (retcode == -100) {
            throw new RuntimeException("stoken已失效，请重新抓取cookie");
        } else {
            throw new RuntimeException("stoke转换产生了其他异常");
        }
    }

    /**
     * 整理cookie，使cookie保存最后出现的东西
     *
     * @param cookies cookie
     * @return 整理后的cookie
     */
    private static String tidyCookie(String cookies) {
        Map<String, String> cookieMap = new HashMap<>();
        String[] cookieArray = cookies.split(";");

        for (String cookie : cookieArray) {
            cookie = cookie.trim();
            if (cookie.isEmpty()) {
                continue;
            }
            String[] keyValue = cookie.split("=", 2);
            if (keyValue.length == 2) {
                cookieMap.put(keyValue[0], keyValue[1]);
            }
        }
        StringBuilder tidyCookies = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (!tidyCookies.isEmpty()) {
                tidyCookies.append("; ");
            }
            tidyCookies.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return tidyCookies.toString();
    }

    protected static String getStokenCookie(String stoken, String mid, String stuid) {
        if (stoken.startsWith("v2_"))
            return "stoken=" + stoken + ";stuid=" + stuid + ";mid=" + mid;
        String[] v2 = v1tov2(stuid, stoken, mid);
        return "stoken=" + v2[0] + ";stuid=" + v2[2] + ";mid=" + v2[1];
    }

    protected static String[] getPassChallenge(Map<String, String> headers) {
     /*   String response = sendGetRequest("https://bbs-api.miyoushe.com/misc/api/createVerification?is_high=true", headers, null);
        JsonObject data = JsonParser.parseString(response).getAsJsonObject();
        if (data.get("retcode").getAsInt() != 0) {
            return null;
        }
        String validate = tools.captcha(data.getAsJsonObject("data").get("gt").getAsString(), data.getAsJsonObject("data").get("challenge").getAsString());
        if (validate != null) {
            Map<String, Object> body = new HashMap<>();
            body.put("geetest_challenge", data.getAsJsonObject("data").get("challenge").getAsString());
            body.put("geetest_seccode", validate + "|jordan");
            body.put("geetest_validate", validate);
            String checkResponse = sendPostRequest("https://bbs-api.miyoushe.com/misc/api/verifyVerification", headers, body);
            JsonObject check = JsonParser.parseString(checkResponse).getAsJsonObject();
            if (check.get("retcode").getAsInt() == 0 && check.getAsJsonObject("data").get("challenge").getAsString() != null) {
                return new String[]{check.getAsJsonObject("data").get("challenge").getAsString(), validate,data.getAsJsonObject("data").get("gt").getAsString()};
            }
        }
        return null;*/
        return start.start_service1(headers);
    }

    /**
     * 获取任务列表，判断还有什么任务没有执行
     */
    private void getTasksList() {
        notifier.notifyListeners("正在获取任务列表");
        String response = sendGetRequest("https://bbs-api.miyoushe.com/apihub/sapi/getUserMissionsState", bbs_headers, null);
        JsonObject res = JsonParser.parseString(response).getAsJsonObject();
        JsonObject data = JsonParser.parseString(response).getAsJsonObject().get("data").getAsJsonObject();
        if (res.get("message").getAsString().contains("err") || res.get("retcode").getAsInt() == -100) {
            throw new RuntimeException("获取任务列表失败，你的cookie可能已过期，请重新设置cookie。");
        }
        this.todayGetCoins = data.get("can_get_points").getAsInt();
        this.todayHaveGetCoins = data.get("already_received_points").getAsInt();
        this.haveCoins = data.get("total_points").getAsInt();
        Map<Integer, Map<String, String>> tasks = new HashMap<>();
        tasks.put(58, Map.of("attr", "sign", "done", "is_get_award"));
        tasks.put(59, Map.of("attr", "read", "done", "is_get_award", "num_attr", "read_num"));
        tasks.put(60, Map.of("attr", "like", "done", "is_get_award", "num_attr", "like_num"));
        tasks.put(61, Map.of("attr", "share", "done", "is_get_award"));
        if (this.todayGetCoins == -1) {
            this.taskDo.put("sign", true);
            this.taskDo.put("read", true);
            this.taskDo.put("like", true);
            this.taskDo.put("share", true);
        } else {
            for (int task : tasks.keySet()) {
                JsonObject missionState = null;
                for (JsonElement stateElement : data.get("states").getAsJsonArray()) {
                    JsonObject state = stateElement.getAsJsonObject();
                    if (state.get("mission_id").getAsInt() == task) {
                        missionState = state;
                        break;
                    }
                }
                if (missionState == null) {
                    continue;
                }
                Map<String, String> doTask = tasks.get(task);
                if (missionState.get(doTask.get("done")).getAsBoolean()) {
                    this.taskDo.put(doTask.get("attr"), true);
                } else if (doTask.containsKey("num_attr")) {
                    this.taskTimes.put(doTask.get("num_attr"),
                            this.taskTimes.get(doTask.get("num_attr")) - missionState.get("happened_times").getAsInt());
                }
            }
        }
        if (data.get("can_get_points").getAsInt() != 0) {
            boolean newDay = data.get("states").getAsJsonArray().get(0).getAsJsonObject().get("mission_id").getAsInt() >= 62;
            if (newDay) notifier.notifyListeners("新的一天，今天可以获得" + this.todayGetCoins + "个米游币");
            else notifier.notifyListeners("似乎还有任务没完成，今天还能获得" + this.todayGetCoins + "个米游币");
        } else notifier.notifyListeners("今天已经完成了所有米游币任务，明天再来吧");
    }

    /**
     * 获取帖子列表
     *
     * @return 帖子列表
     */
    private List<List<String>> getList() {
        if (todayGetCoins == 0)
            return null;
        List<List<String>> tempList = new ArrayList<>();
        notifier.notifyListeners("正在获取帖子列表......");
        String response = sendGetRequest("https://bbs-api.miyoushe.com/post/api/getForumPostList", bbs_headers,
                Map.of("forum_id", this_bbsList.getFirst().get("forumId"), "is_good", "false", "is_hot", "false", "page_size", "20", "sort_type", "1"));
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
        JsonObject data = jsonObject.getAsJsonObject("data");
        JsonArray list = data.getAsJsonArray("list");
        for (JsonElement postElement : list) {
            JsonObject post = postElement.getAsJsonObject().getAsJsonObject("post");
            tempList.add(List.of(post.get("post_id").getAsString(), post.get("subject").getAsString()));
            if (tempList.size() >= 5) {
                break;
            }
            notifier.notifyListeners("已获取" + tempList.size() + "个帖子");
        }
        return tempList;
    }

    /**
     * 签到米游币
     */
    private void signPosts() {
        if (this.taskDo.get("sign")) {
            notifier.notifyListeners("讨论区任务(每日签到)已经完成过了~");
            return;
        }
        notifier.notifyListeners("正在签到......");
        Map<String, String> header = new HashMap<>(bbs_headers);
        for (Map<String, String> forum : this_bbsList) {
            String challenge = null;
            for (int retryCount = 0; retryCount < 3; retryCount++) {
                // 构建 Map 类型的请求体
                Map<String, Object> postDataMap = new HashMap<>();
                postDataMap.put("gids", Integer.parseInt(forum.get("id")));
                // 生成 DS 签名
                String postDataJson = new Gson().toJson(postDataMap);
                header.put("DS", getDS2(postDataJson, SALT_6X, ""));
                String response = sendPostRequest("https://bbs-api.miyoushe.com/apihub/app/api/signIn", header, postDataMap);
                JsonObject data = JsonParser.parseString(response).getAsJsonObject();
                if (data.get("retcode").getAsInt() == 1034) {
                    notifier.notifyListeners("社区签到触发验证码");
                    notifier.notifyListeners("请打开网页：http://127.0.0.1:8080/verify-geetest.html 通过验证码");
                    String[] temp = getPassChallenge(header);
                    if (temp != null) {
                        challenge = temp[0];
                        header.put("x-rpc-challenge", challenge);
                    }
                } else if (!data.get("message").getAsString().contains("err") && data.get("retcode").getAsInt() == 0) {
                    notifier.notifyListeners(forum.get("name") + data.get("message").getAsString());
                    wait1();
                    break;
                } else if (data.get("retcode").getAsInt() == -100) {
                    throw new RuntimeException("签到失败，你的cookie可能已过期，请重新设置cookie。");
                } else {
                    notifier.notifyListeners("未知错误: " + response);
                }
            }
            if (challenge != null) {
                header.remove("x-rpc-challenge");
            }
        }
    }

    /**
     * 看帖任务
     */
    private void readPosts() {
        if (this.taskDo.get("read")) {
            notifier.notifyListeners("看帖任务已经完成过了~");
            return;
        }
        notifier.notifyListeners("正在看帖......");
        for (int i = 0; i < this.taskTimes.get("read_num"); i++) {
            String response = sendGetRequest("https://bbs-api.miyoushe.com/post/api/getPostFull", bbs_headers,
                    Map.of("post_id", this.postsList.get(i).get(0)));
            JsonObject data = JsonParser.parseString(response).getAsJsonObject();
            if (data.get("message").getAsString().equals("OK")) {
                notifier.notifyListeners("看帖：" + this.postsList.get(i).get(1) + " 成功");
            }
            wait1();
        }
    }

    /**
     * 点赞任务
     */
    private void likePosts() {
        Map<String, String> header = new HashMap<>(bbs_headers);
        String challenge = null;
        if (this.taskDo.get("like")) {
            notifier.notifyListeners("点赞任务已经完成过了~");
            return;
        }
        notifier.notifyListeners("正在点赞......");
        for (int i = 0; i < this.taskTimes.get("like_num"); i++) {
            String response = sendPostRequest("https://bbs-api.miyoushe.com/apihub/sapi/upvotePost", header,
                    Map.of("post_id", this.postsList.get(i).get(0), "is_cancel", "false"));
            JsonObject data = JsonParser.parseString(response).getAsJsonObject();
            if (data.get("message").getAsString().equals("OK")) {
                notifier.notifyListeners("点赞：" + this.postsList.get(i).get(1) + " 成功");
                if (challenge != null) {
                    challenge = null;
                    header.remove("x-rpc-challenge");
                }
                wait1();
                response = sendPostRequest("https://bbs-api.miyoushe.com/apihub/sapi/upvotePost", header,
                        Map.of("post_id", this.postsList.get(i).get(0), "is_cancel", "true"));
                if (JsonParser.parseString(response).getAsJsonObject().get("message").getAsString().equals("OK")) {
                    notifier.notifyListeners("取消点赞：" + this.postsList.get(i).get(1) + " 成功");
                }
            } else if (data.get("retcode").getAsInt() == 1034) {
                notifier.notifyListeners("点赞触发验证码");
                notifier.notifyListeners("请打开网页：http://127.0.0.1:8080/verify-geetest.html 通过验证码");
                String[] temp = getPassChallenge(header);
                if (temp != null) {
                    challenge = temp[0];
                    header.put("x-rpc-challenge", challenge);
                }
            }
            wait1();
        }
    }

    /**
     * 分享任务
     */
    private void sharePost() {
        if (this.taskDo.get("share")) {
            notifier.notifyListeners("分享任务已经完成过了~");
        } else {
            notifier.notifyListeners("正在执行分享任务......");
            for (int i = 0; i < 3; i++) {
                String response = sendGetRequest("https://bbs-api.miyoushe.com/apihub/api/getShareConf", bbs_headers,
                        Map.of("entity_id", this.postsList.getFirst().getFirst(), "entity_type", "1"));
                JsonObject data = JsonParser.parseString(response).getAsJsonObject();
                if (data.get("message").getAsString().equals("OK")) {
                    notifier.notifyListeners("分享：" + this.postsList.getFirst().get(1) + " 成功");
                    notifier.notifyListeners("分享任务执行成功......");
                    break;
                }
                notifier.notifyListeners("分享任务执行失败，正在执行第" + (i + 2) + "次，共3次");
                wait1();
            }
            wait1();
        }
    }

    /**
     * 运行签到任务（米游币）
     *
     * @param sign  是否签到（米游币）
     * @param read  是否看帖
     * @param like  是否点赞
     * @param share 是否分享
     */
    public void runTask(Boolean sign, Boolean read, Boolean like, Boolean share) {
        String returnData = "米游社: ";
        if (this.taskDo.get("sign") && this.taskDo.get("read") && this.taskDo.get("like") && this.taskDo.get("share")) {
            returnData += "\n" + "今天已经全部完成了！\n" + "一共获得" + this.todayHaveGetCoins + "个米游币\n目前有" + this.haveCoins + "个米游币";
            notifier.notifyListeners(returnData);
        } else {
            int i = 0;
            while (this.todayGetCoins != 0 && i < 3) {
                if (i > 0) postsList = getList();
                if (sign && !taskDo.get("sign")) signPosts();
                if (read && !taskDo.get("read")) readPosts();
                if (like && !taskDo.get("like")) likePosts();
                if (share && !taskDo.get("share")) sharePost();
                getTasksList();
                i++;
            }
            returnData += "\n" + "今天已经获得" + this.todayHaveGetCoins + "个米游币\n" + "还能获得" + this.todayGetCoins + "个米游币\n目前有" + this.haveCoins + "个米游币";
            notifier.notifyListeners(returnData);
            wait1();
        }
    }

    /**
     * 运行游戏签到任务 -领取游戏的每日签到奖励
     *
     * @param game 游戏名（崩坏2，崩坏3，未定事件簿，原神，星铁，绝区零）
     */
    public void gameTask(String[] game) {
        bbs_game_daily.run_task(game, notifier);
    }

    private void wait1() {
        try {
            Thread.sleep(new Random().nextInt(7) * 1000 + 2000);
        } catch (InterruptedException e) {
            throw new RuntimeException("多线程等待出错" + e);
        }
    }

    protected static class bbs_game_daily {

        private static tools.StatusNotifier statusNotifier;
        protected final Map<String, String> game_login_headers_this;
        private final String game_name;
        private final String act_id;
        private final String player_name;
        private final String game_id;
        private List<Map<String, String>> account_list;
        private String rewards_api;
        private String is_sign_api;
        private String sign_api;
        private List<Map<String, Object>> checkin_rewards;

        /**
         * @param game_id     游戏id
         * @param game_name   游戏名
         * @param act_id      游戏代码
         * @param player_name 玩家称呼
         */
        private bbs_game_daily(String game_id, String game_name, String act_id, String player_name) {
            this.game_name = game_name;
            this.game_login_headers_this = new HashMap<>(fixed.game_login_headers);
            this.act_id = act_id;
            this.player_name = player_name;
            this.game_id = game_id;
            this.rewards_api = "https://api-takumi.mihoyo.com/event/luna/home";
            this.is_sign_api = "https://api-takumi.mihoyo.com/event/luna/info";
            this.sign_api = "https://api-takumi.mihoyo.com/event/luna/sign";
            this.checkin_rewards = new ArrayList<>();
            switch (act_id) {
                case Honkai2_act_id ->
                        game_login_headers_this.put("Referer", "https://webstatic.mihoyo.com/bbs/event/signin/bh2/index.html?bbs_auth_required=true&act_id=" + Honkai2_act_id + "&bbs_presentation_style=fullscreen&utm_source=bbs&utm_medium=mys&utm_campaign=icon");
                case Honkai3rd_act_id ->
                        game_login_headers_this.put("Referer", "https://webstatic.mihoyo.com/bbs/event/signin/bh3/index.html?bbs_auth_required=true&act_id=" + Honkai3rd_act_id + "&bbs_presentation_style=fullscreen&utm_source=bbs&utm_medium=mys&utm_campaign=icon");
                case HonkaiStarRail_act_id -> game_login_headers_this.put("Origin", "https://act.mihoyo.com");
                case Genshin_act_id -> {
                    game_login_headers_this.put("Origin", "https://act.mihoyo.com");
                    game_login_headers_this.put("x-rpc-signgame", "hk4e");
                }
                case ZZZ_act_id -> {
                    game_login_headers_this.put("Referer", "https://act.mihoyo.com");
                    game_login_headers_this.put("X-Rpc-Signgame", "zzz");
                }
                case TearsOfThemis_act_id ->
                        game_login_headers_this.put("Referer", "https://webstatic.mihoyo.com/bbs/event/signin/nxx/index.html?bbs_auth_required=true&bbs_presentation_style=fullscreen&act_id=" + TearsOfThemis_act_id);
            }
        }


        /**
         * 运行签到任务
         *
         * @param game           游戏名（崩坏2，崩坏3，未定事件簿，原神，星铁，绝区零）
         * @param statusNotifier 状态通知器
         */
        private static void run_task(String[] game, tools.StatusNotifier statusNotifier) {
            bbs_game_daily.statusNotifier = statusNotifier;
            List<Map<String, Object>> list = new ArrayList<>(Arrays.asList(new HashMap<>() {{
                put("game_print_name", "崩坏2");
                put("game_name", "honkai2");
            }}, new HashMap<>() {{
                put("game_print_name", "崩坏3");
                put("game_name", "honkai3rd");
            }}, new HashMap<>() {{
                put("game_print_name", "未定事件簿");
                put("game_name", "tears_of_themis");
            }}, new HashMap<>() {{
                put("game_print_name", "原神");
                put("game_name", "genshin");
            }}, new HashMap<>() {{
                put("game_print_name", "星铁");
                put("game_name", "honkai_sr");
            }}, new HashMap<>() {{
                put("game_print_name", "绝区零");
                put("game_name", "zzz");
            }}));
            for (Map<String, Object> map : list) {
                String game_print_name = (String) map.get("game_print_name");
                String game_name = (String) map.get("game_name");
                for (String s : game) {
                    if (s.equals(game_print_name)) {
                        bbs_game_daily game_module = switch (game_name) {
                            case "honkai2" -> new Honkai2(true);
                            case "honkai3rd" -> new Honkai3rd(true);
                            case "tears_of_themis" -> new TearsOfThemis(true);
                            case "genshin" -> new Genshin(true);
                            case "honkai_sr" -> new HonkaiStarRail(true);
                            case "zzz" -> new ZZZ(true);
                            default -> null;
                        };
                        statusNotifier.notifyListeners("正在进行" + game_print_name + "签到");
                        if (game_module != null) {
                            game_module.signAccount();
                        }
                        try {
                            Thread.sleep(new Random().nextInt(10 - 5 + 1) + 2 * 1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Thread系统错误" + e);
                        }
                    }
                }
            }
        }

        /**
         * 更新cookie_token
         */
        private static String updateCookieToken() {
            statusNotifier.notifyListeners("CookieToken失效，尝试刷新");
            String newToken = getCookieTokenByStoken();
            statusNotifier.notifyListeners("CookieToken刷新成功");
            tools.files.write("cookie_token", newToken);
            return newToken;
        }

        /**
         * 获取米哈游账号绑定的指定游戏账号列表
         *
         * @param game_id 游戏id
         * @param headers 请求头
         * @param update  是否更新cookie_token
         * @return 账号列表List<Map < String, String>>, key为region, uid, nickname.
         */
        protected static List<Map<String, String>> getAccountList(String game_id, Map<String, String> headers, Boolean update, tools.StatusNotifier statusNotifier) {
            if (statusNotifier == null)
                statusNotifier = new tools.StatusNotifier();
            String game_name = game_id_to_name.getOrDefault(game_id, game_id);
            if (update) {
                String new_Cookie = updateCookieToken();
                headers.put("Cookie", "cookie_token=" + new_Cookie + ";ltoken=" + tools.files.read().get("ltoken") + ";ltuid=" + tools.files.read().get("stuid") + ";account_id=" + tools.files.read().get("stuid"));
            }
            statusNotifier.notifyListeners("正在获取米哈游账号绑定的" + game_name + "账号列表...");
            String response = sendGetRequest("https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie", headers, Map.of("game_biz", game_id));
            JsonObject data = JsonParser.parseString(response).getAsJsonObject();
            if (data.get("retcode").getAsInt() == -100) {
                return getAccountList(game_id, headers, true, statusNotifier);
            }
            if (data.get("retcode").getAsInt() != 0) {
                statusNotifier.notifyListeners("获取" + game_name + "账号列表失败！");
                return new ArrayList<>();
            }
            List<Map<String, String>> account_list = new ArrayList<>();
            for (var entry : data.getAsJsonObject("data").getAsJsonArray("list").asList()) {
                JsonObject account = entry.getAsJsonObject();
                Map<String, String> accountInfo = new HashMap<>();
                accountInfo.put("nickname", account.get("nickname").getAsString());
                accountInfo.put("game_uid", account.get("game_uid").getAsString());
                accountInfo.put("region", account.get("region").getAsString());
                accountInfo.put("game_biz", account.get("game_biz").getAsString());
                account_list.add(accountInfo);
            }
            tools.files.write(game_id + "_user", new Gson().toJson(account_list));
            statusNotifier.notifyListeners("已获取到" + account_list.size() + "个" + game_name + "账号信息");
            return account_list;
        }

        /**
         * 给不同游戏初始化对象方法
         */
        private void init(Boolean b) {
            game_login_headers_this.put("DS", game_login_headers.get("DS"));
            game_login_headers_this.put("Cookie", "cookie_token=" + tools.files.read().get("cookie_token") + ";ltoken=" + tools.files.read().get("ltoken") + ";ltuid=" + tools.files.read().get("stuid") + ";account_id=" + tools.files.read().get("stuid"));
            this.account_list = getAccountList(game_id, game_login_headers_this, false, statusNotifier);
            if (!account_list.isEmpty() && b) {
                this.checkin_rewards = getCheckinRewards();
            }
        }

        /**
         * 获取签到奖励列表
         *
         * @return 签到奖励列表List<Map < String, Object>>, key为name名称, cnt数量.
         */
        private List<Map<String, Object>> getCheckinRewards() {
            statusNotifier.notifyListeners("正在获取签到奖励列表...");
            int max_retry = 3;
            for (int i = 0; i < max_retry; i++) {
                game_login_headers_this.put("DS", game_login_headers.get("DS"));
                String response = sendGetRequest(rewards_api, game_login_headers_this, Map.of("lang", "zh-cn", "act_id", act_id));
                JsonObject data = JsonParser.parseString(response).getAsJsonObject();
                if (data.get("retcode").getAsInt() == 0) {
                    JsonArray awardsArray = data.getAsJsonObject("data").getAsJsonArray("awards");
                    List<Map<String, Object>> rewards = new ArrayList<>();
                    for (JsonElement award : awardsArray) {
                        JsonObject awardObject = award.getAsJsonObject();
                        Map<String, Object> rewardMap = new HashMap<>();
                        rewardMap.put("name", awardObject.get("name").getAsString());
                        rewardMap.put("cnt", awardObject.get("cnt").getAsString());
                        rewards.add(rewardMap);
                    }
                    return rewards;
                }
                statusNotifier.notifyListeners("获取签到奖励列表失败，重试次数: " + (i + 1));
                try {
                    Thread.sleep(new Random().nextInt(8 - 2 + 1) + 2 * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("thread异常" + e);
                }
            }
            statusNotifier.notifyListeners("获取签到奖励列表失败");
            return new ArrayList<>();
        }

        /**
         * 获取账号签到信息
         *
         * @param region 游戏区
         * @param uid    游戏uid
         * @param update 是否更新cookie_token
         * @return 签到信息Map<String, Object>, key为is_sign是否已签到, remain_days剩余天数, sign_cnt已签到天数, first_bind是否首次绑定.
         */
        private Map<String, Object> isSign(String region, String uid, boolean update) {
            String response = sendGetRequest(this.is_sign_api, game_login_headers_this, Map.of("lang", "zh-cn", "act_id", act_id, "region", region, "uid", uid));
            JsonObject data = JsonParser.parseString(response).getAsJsonObject();
            if (data.get("retcode").getAsInt() != 0) {
                if (!update) {
                    String new_cookie = updateCookieToken();
                    game_login_headers_this.put("DS", game_login_headers.get("DS"));
                    game_login_headers_this.put("Referer", "https://act.mihoyo.com/");
                    game_login_headers_this.put("Cookie", new_cookie);
                    return isSign(region, uid, true);
                }
                throw new RuntimeException("BBS Cookie Errror" + "获取账号签到信息失败！" + response);
            }
            Map<String, Object> resultMap = new HashMap<>();
            JsonObject dataObject = data.getAsJsonObject("data");
            for (Map.Entry<String, JsonElement> entry : dataObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isNumber()) {
                        resultMap.put(entry.getKey(), value.getAsNumber());
                    } else if (value.getAsJsonPrimitive().isBoolean()) {
                        resultMap.put(entry.getKey(), value.getAsBoolean());
                    } else {
                        resultMap.put(entry.getKey(), value.getAsString());
                    }
                } else if (value.isJsonObject()) {
                    resultMap.put(entry.getKey(), value.getAsJsonObject().toString());
                } else if (value.isJsonArray()) {
                    resultMap.put(entry.getKey(), value.getAsJsonArray().toString());
                } else {
                    resultMap.put(entry.getKey(), value.toString());
                }
            }
            return resultMap;
        }

        /**
         * 签到
         *
         * @param account 账号信息Map<String, String>, key为nickname昵称, game_uid游戏uid, region游戏区.
         * @return 签到结果String
         */
        private String checkIn(Map<String, String> account) {
            Map<String, String> header = new HashMap<>(game_login_headers_this);
            int retries = 3;
            String response = "";
            for (int i = 1; i <= retries; i++) {
                if (i > 1)
                    statusNotifier.notifyListeners("触发验证码，即将进行第 " + i + " 次重试，最多 " + retries + " 次");
                response = sendPostRequest(sign_api, header, Map.of("act_id", act_id, "region", account.get("region"), "uid", account.get("game_uid")));
                JsonObject data = JsonParser.parseString(response).getAsJsonObject();
                if (data.get("retcode").getAsInt() == 429) {
                    try {
                        Thread.sleep(10000); // 429同ip请求次数过多，尝试sleep10s进行解决
                    } catch (InterruptedException e) {
                        throw new RuntimeException("thread系统错误");
                    }
                    statusNotifier.notifyListeners("429 Too Many Requests ，即将进入下一次请求");
                    continue;
                }
                if (data.get("retcode").getAsInt() == 0 && data.getAsJsonObject("data").get("success").getAsInt() != 0 && i < retries) {
                    statusNotifier.notifyListeners("请打开网页：http://127.0.0.1:8080/verify-geetest.html 通过验证码");
                    String[] temp = bbs_daily.getPassChallenge(record_headers);
                    if (temp != null) {
                        String validate = temp[1];
                        header.put("x-rpc-challenge", data.getAsJsonObject("data").get("challenge").getAsString());
                        header.put("x-rpc-validate", validate);
                        header.put("x-rpc-seccode", validate + "|jordan");
                    }
                    try {
                        Thread.sleep(new Random().nextInt(15 - 6 + 1) + 6 * 1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("系统错误");
                    }
                } else {
                    break;
                }
            }
            return response;
        }

        /**
         * 签到(最开始的)
         */
        private void signAccount() {
            statusNotifier.notifyListeners(game_name + ": ");
            if (account_list.isEmpty()) {
                statusNotifier.notifyListeners("签到失败，并没有绑定任何" + game_name + "账号，请先绑定");
                return;
            }
            try {
                for (Map<String, String> account : account_list) {
                    Thread.sleep(new Random().nextInt(8 - 2 + 1) + 2 * 1000);
                    Map<String, Object> isData = isSign(account.get("region"), account.get("game_uid"), false);
                    if (isData.get("first_bind") != null && (Boolean) isData.get("first_bind")) {
                        statusNotifier.notifyListeners(player_name + account.get("nickname") + "是第一次绑定米游社，请先手动签到一次");
                        continue;
                    }
                    int signDays = ((Number) isData.get("total_sign_day")).intValue() - 1;
                    if ((Boolean) isData.get("is_sign")) {
                        statusNotifier.notifyListeners(player_name + account.get("nickname") + "今天已经签到过了~");
                        signDays += 1;
                    } else {
                        Thread.sleep(new Random().nextInt(8 - 2 + 1) + 2 * 1000);
                        String req = checkIn(account);
                        JsonObject data = JsonParser.parseString(req).getAsJsonObject();
                        if (data.get("retcode").getAsInt() != 429) {
                            if (data.get("retcode").getAsInt() == 0 && data.getAsJsonObject("data").get("success").getAsInt() == 0) {
                                statusNotifier.notifyListeners(player_name + account.get("nickname") + "签到成功~");
                                signDays += 2;
                            } else if (data.get("retcode").getAsInt() == -5003) {
                                statusNotifier.notifyListeners(player_name + account.get("nickname") + "今天已经签到过了~");
                            } else {
                                String s = "账号签到失败！\n" + req + "\n";
                                if (!data.get("data").isJsonNull() && data.getAsJsonObject("data").has("success") && data.getAsJsonObject("data").get("success").getAsInt() != 0) {
                                    s += "原因: 验证码\njson信息:" + req;
                                }
                                statusNotifier.notifyListeners(s);
                                continue;
                            }
                        } else {
                            statusNotifier.notifyListeners(account.get("nickname") + "，本次签到失败");
                            continue;
                        }
                    }
                    statusNotifier.notifyListeners(account.get("nickname") + "已连续签到" + signDays + "天");
                    statusNotifier.notifyListeners("今天获得的奖励是" + checkin_rewards.get(signDays - 1).get("name") + "x" + checkin_rewards.get(signDays - 1).get("cnt"));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("thread异常" + e);
            }
        }

        private static class Honkai2 extends bbs_game_daily {

            public Honkai2(Boolean b) {
                super(fixed.name_to_game_id("崩坏2"), "崩坏学园2", Honkai2_act_id, "舰长");
                super.init(b);
            }
        }

        private static class Honkai3rd extends bbs_game_daily {

            public Honkai3rd(Boolean b) {
                super(fixed.name_to_game_id("崩坏3"), "崩坏3", Honkai3rd_act_id, "舰长");
                super.init(b);
            }
        }

        private static class HonkaiStarRail extends bbs_game_daily {

            public HonkaiStarRail(Boolean b) {
                super(fixed.name_to_game_id("星铁"), "崩坏: 星穹铁道", HonkaiStarRail_act_id, "开拓者");
                super.init(b);
            }
        }

        protected static class Genshin extends bbs_game_daily {
            protected Genshin(Boolean b) {
                super(fixed.name_to_game_id("原神"), "原神", Genshin_act_id, "旅行者");
                super.init(b);
            }

            protected static List<Map<String, String>> getAccountList(tools.StatusNotifier notifier) {
                bbs_game_daily.statusNotifier = notifier;
                Genshin genshin = new Genshin(false);
                genshin.game_login_headers_this.put("DS", game_login_headers.get("DS"));
                return bbs_game_daily.getAccountList(fixed.name_to_game_id("原神"), genshin.game_login_headers_this, false, statusNotifier);
            }
        }

        private static class ZZZ extends bbs_game_daily {

            public ZZZ(Boolean b) {
                super(fixed.name_to_game_id("绝区零"), "绝区零", ZZZ_act_id, "绳匠");
                super.rewards_api = "https://act-nap-api.mihoyo.com/event/luna/zzz/home";
                super.is_sign_api = "https://act-nap-api.mihoyo.com/event/luna/zzz/info";
                super.sign_api = "https://act-nap-api.mihoyo.com/event/luna/zzz/sign";
                super.init(b);
            }
        }

        private static class TearsOfThemis extends bbs_game_daily {

            public TearsOfThemis(Boolean b) {
                super(fixed.name_to_game_id("未定事件簿"), "未定事件簿", TearsOfThemis_act_id, "律师");
                super.init(b);
            }
        }
    }

}


package com.muxiao;

import com.google.gson.*;
import org.jsoup.Jsoup;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static com.muxiao.fixed.widget_headers;

public class get_article {

    /**
     * 获取帖子列表信息
     *
     * @param name           游戏名 可填：崩坏3、原神、崩坏2、未定事件簿、大别野、星铁、绝区零
     * @param type           写123：1公告、2活动、3资讯（就是米游社上面的分类）
     * @param statusNotifier 监听器
     * @param page_size      每页数量
     * @return List&lt;Map&lt;String, String&gt;&gt; key： 文章id、文章标题、文章简介、文章首页图
     */
    public static List<Map<String, String>> get_article_list(String name, int type, tools.StatusNotifier statusNotifier, int page_size) {
        List<Map<String, String>> articleList = new ArrayList<>();
        String gids = null;
        for (Map<String, String> map : fixed.bbs_list) {
            if (map.get("name").equals(name)) {
                gids = map.get("id");
            }
        }
        if (gids == null)
            throw new RuntimeException("请输入正确的游戏名");
        if (type != 1 && type != 2 && type != 3)
            throw new RuntimeException("请输入正确的资讯类型");
        for (int i = 1; i <= 3; i++) {
            String response = tools.sendGetRequest("https://bbs-api-static.miyoushe.com/painter/api/getNewsList", new HashMap<>(),
                    Map.of("gids", gids, "type", String.valueOf(type), "page_size", String.valueOf(page_size), "client_type", "2", "is_official_tab", "true", "last_id", ""));
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            if (jsonObject.get("retcode").getAsInt() != 0) {
                statusNotifier.notifyListeners("获取失败，正在重试,当前重试次数：" + i);
                continue;
            }
            JsonObject data = jsonObject.getAsJsonObject("data");
            JsonArray list = data.getAsJsonArray("list");
            for (JsonElement postElement : list) {
                JsonObject post = postElement.getAsJsonObject().getAsJsonObject("post");
                articleList.add(Map.of("文章id", post.get("post_id").getAsString(), "文章标题", post.get("subject").getAsString(),
                        "文章简介", post.get("content").getAsString(), "文章首页图", post.getAsJsonArray("images").get(0).getAsString()));
            }
            statusNotifier.notifyListeners("已获取" + articleList.size() + "个帖子");
            for (Map<String, String> map : articleList) {
                System.out.println(map.get("文章id") + " " + map.get("文章标题") + " " + map.get("文章简介") + " " + map.get("文章首页图"));
            }
            break;
        }
        return articleList;
    }

    /**
     * 获取帖子列表信息（默认20条）
     *
     * @param name           游戏名 可填：崩坏3、原神、崩坏2、未定事件簿、大别野、星铁、绝区零
     * @param type           写123：1公告、2活动、3资讯（就是米游社上面的分类）
     * @param statusNotifier 监听器
     * @return List&lt;Map&lt;String, String&gt;&gt; key： 文章id、文章标题、文章简介、文章首页图
     */
    public static List<Map<String, String>> get_article_list(String name, int type, tools.StatusNotifier statusNotifier) {
        return get_article_list(name, type, statusNotifier, 20);
    }

    /**
     * 获取帖子html代码
     *
     * @param article_id 帖子id
     */
    public static String get_article_htmlCode(String article_id) {
        for (int i = 1; i <= 3; i++) {
            String response = tools.sendGetRequest("https://bbs-api.miyoushe.com/post/api/getPostFull", fixed.bbs_headers,
                    Map.of("post_id", article_id, "csm_source", ""));
            JsonObject data = JsonParser.parseString(response).getAsJsonObject();
            if (data.get("message").getAsString().equals("OK")) {
                JsonObject post = data.getAsJsonObject("data").getAsJsonObject("post").getAsJsonObject("post");
                return "<head><meta charset=\"UTF-8\"><title>" + post.get("subject").getAsString() + "</title></head>" + post.get("content").getAsString();
            }
        }
        return null;
    }

    /**
     * 获取帖子的纯文本
     *
     * @param article_id 帖子id
     */
    public static String get_article_only_text(String article_id) {
        String text = Jsoup.parse(Objects.requireNonNull(get_article_htmlCode(article_id))).text();
        text = text.replaceAll("超链图片", "");
        return text;
    }

    /**
     * 获取奖励的消息通知
     */
    private static String get_award(String channel_id) {
        String cookie = bbs_daily.getStokenCookie(tools.files.read().get("stoken"), tools.files.read().get("mid"), tools.files.read().get("stuid"));
        Map<String, String> headers = new HashMap<>(fixed.bbs_headers);
        headers.put("Cookie", cookie);
        String response = tools.sendGetRequest("https://bbs-api.miyoushe.com/notification/api/v2/getUserChannelNotifications", headers,
                Map.of("channel_id", channel_id, "size", "20", "offset", ""));
        JsonObject res = JsonParser.parseString(response).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        if (res.get("message").getAsString().equals("OK")) {
            JsonObject data = res.getAsJsonObject("data");
            sb.append(data.getAsJsonObject("channel").get("channel_name").getAsString()).append(" :\n");
            for (JsonElement element : data.getAsJsonArray("list")) {
                JsonObject jsonObject = element.getAsJsonObject();
                String title = jsonObject.get("site_title").getAsString();
                String content = jsonObject.get("site_content").getAsString();
                String url = "https://www.miyoushe.com/ys" + jsonObject.get("web_path").getAsString();
                if (jsonObject.get("web_path").getAsString().isEmpty())
                    url = jsonObject.get("app_path").getAsString();
                long time = jsonObject.get("created_at").getAsLong() * 1000L;
                String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));
                sb.append("\n标题： ").append(title).append("\n内容： ").append(content).append("\n发送时间 ：").append(formattedDate).append("\n附加链接： ").append(url).append("\n");
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * 获取奖励的消息通知 -类型1
     */
    public static String get_award() {
        return get_award("ch_receive_award");
    }

    /**
     * 获取奖励的消息通知 -类型2
     */
    public static String get_award2() {
        return get_award("ch_award");
    }

    /**
     * 发送评论
     *
     * @param content 评论内容
     * @param post_id 帖子id
     */
    public static String sent_reply(String content, String post_id) {
        String cookie = bbs_daily.getStokenCookie(tools.files.read().get("stoken"), tools.files.read().get("mid"), tools.files.read().get("stuid"));
        Map<String, String> headers = new HashMap<>(fixed.bbs_headers);
        headers.put("Cookie", cookie);
        String response = tools.sendPostRequest("https://bbs-api.miyoushe.com/post/api/releaseReply", headers,
                Map.of("content", content, "csm_source", "official", "post_id", post_id, "reply_id", "", "structured_content", "[{\"insert\":\"" + content + "\"}]"));
        return JsonParser.parseString(response).getAsJsonObject().get("message").getAsString();
    }

    /**
     * 获取评论
     *
     * @param post_id 帖子id
     */
    public static String[] get_reply(String post_id) {
        String response = tools.sendGetRequest("https://bbs-api.miyoushe.com/post/api/getPostReplies", fixed.bbs_headers,
                Map.of("post_id", post_id, "order_type", "3", "size", "50", "only_master", "false", "is_hot", "true", "last_id", "", "from_external_link", "false"));
        ArrayList<String> reply = new ArrayList<>();
        JsonObject res = JsonParser.parseString(response).getAsJsonObject();
        if (res.get("message").getAsString().equals("OK")) {
            JsonObject data = res.getAsJsonObject("data");
            for (JsonElement element : data.getAsJsonArray("list")) {
                JsonObject jsonObject = element.getAsJsonObject();
                reply.add(jsonObject.getAsJsonObject("reply").get("content").getAsString());
            }
            Collections.shuffle(reply);
            return reply.subList(0, 20).toArray(new String[0]);
        }
        return null;
    }

    /**
     * 分享帖子
     *
     * @param post_id 帖子id
     */
    public static String share(String post_id) {
        String cookie = bbs_daily.getStokenCookie(tools.files.read().get("stoken"), tools.files.read().get("mid"), tools.files.read().get("stuid"));
        Map<String, String> headers = new HashMap<>(fixed.bbs_headers);
        Map<String, Object> body = new HashMap<>() {{
            put("forward_id", post_id);
            put("csm_source", "official");
            put("structured_content", "");
            put("forward_show_instant_id_list", new ArrayList<String>());
            put("image_list", new ArrayList<String>());
            put("link_card_ids", new ArrayList<String>());
            put("topic_id_list", new ArrayList<String>());
            put("forward_type", "1");
        }};
        headers.put("Cookie", cookie);
        String response = tools.sendPostRequest("https://bbs-api.miyoushe.com/instant/api/instant", headers, body);
        return JsonParser.parseString(response).getAsJsonObject().get("message").getAsString();
    }

    /**
     * 获取米游社的小红点-json
     *
     * @return String
     * */
    public static String reddot(){
        widget_headers.put("cookie", "stuid=" + tools.files.read().get("stuid") + ";stoken=" + tools.files.read().get("stoken") + ";mid=" + tools.files.read().get("mid") + ";");
        return tools.sendGetRequest("https://bbs-api.miyoushe.com/apihub/api/myselfPageConfig", widget_headers, new HashMap<>());
    }
}

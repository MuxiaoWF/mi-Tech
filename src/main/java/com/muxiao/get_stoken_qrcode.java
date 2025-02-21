package com.muxiao;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import com.muxiao.tools.StatusNotifier;

import static com.muxiao.fixed.*;

public class get_stoken_qrcode {

    private static String[] tempStoken;
    private static JFrame frame;
//todo
    static String getDS2(String body, String salt,String params) {
        String i = String.valueOf(System.currentTimeMillis() / 1000);
        String r = String.valueOf(new Random().nextInt(100001, 200000));
        String c = md5("salt=" + salt + "&t=" + i + "&r=" + r + "&b=" + body + "&q="+params);
        return i + "," + r + "," + c;
    }

    protected static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static Tuple<String, String, String, String> getQrUrl() {
        String appId = "2";
        Map<String, Object> body = new HashMap<>() {{
            put("app_id", appId);
            put("device", deviceId);
        }};
        String response = tools.sendPostRequest("https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/fetch", new HashMap<>(), body);
        JsonObject result = new Gson().fromJson(response, JsonObject.class);
        int retcode = result.get("retcode").getAsInt();
        if (retcode != 0) {
            throw new RuntimeException("扫码获取stoken失败-create(RETCODE = " + retcode + "),返回信息为：" + response);
        }
        JsonObject data = result.getAsJsonObject("data");
        String qrUrl = data.get("url").getAsString();
        String ticket = qrUrl.split("ticket=")[1];
        return new Tuple<>(qrUrl, appId, ticket, deviceId);
    }
    protected static void get_ltoken(){
        bbs_headers.put("Cookie","stoken="+tools.files.read().get("stoken")+";mid="+tools.files.read().get("mid"));
        String response = tools.sendGetRequest("https://passport-api.mihoyo.com/account/auth/api/getLTokenBySToken", bbs_headers, new HashMap<>());
        JsonObject result = new Gson().fromJson(response, JsonObject.class);
        if (result.get("retcode").getAsInt() != 0) {
            throw new RuntimeException("获取ltoken失败-getLTokenBySToken(RETCODE = " + result.get("retcode").getAsInt() + "),返回信息为：" + response);
        }
        JsonObject data = result.getAsJsonObject("data");
        String ltoken = data.get("ltoken").getAsString();
        tools.files.write("ltoken", ltoken);
    }
    private static String[] checkLogin(String appId, String ticket, String device, StatusNotifier notifier) {
        try {
            int times = 0;
            while (true) {
                times++;
                Map<String, Object> body = new HashMap<>() {{
                    put("app_id", appId);
                    put("ticket", ticket);
                    put("device", device);
                }};

                String response = tools.sendPostRequest("https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/query", new HashMap<>(),body);
                JsonObject result = new Gson().fromJson(response, JsonObject.class);
                int retcode = result.get("retcode").getAsInt();
                if (retcode != 0) {
                    throw new RuntimeException("扫码获取stoken失败-query(RETCODE = " + retcode + "),返回信息为：" + response);
                }
                JsonObject data = result.getAsJsonObject("data");
                String stat = data.get("stat").getAsString();
                switch (stat) {
                    case "Init":
                        notifier.notifyListeners("等待扫码"+times);
                        break;
                    case "Scanned":
                        notifier.notifyListeners("等待确认"+times);
                        break;
                    case "Confirmed":
                        notifier.notifyListeners("登录成功");
                        // 检查 payload 和 raw 是否存在且不为 null
                        if (!data.has("payload") || data.get("payload").isJsonNull()) {
                            throw new RuntimeException("响应中缺少 payload 字段");
                        }
                        JsonObject payload = data.getAsJsonObject("payload");
                        if (!payload.has("raw") || payload.get("raw").isJsonNull()) {
                            throw new RuntimeException("响应中缺少 raw 字段");
                        }
                        JsonElement rawElement = payload.get("raw");
                        if (rawElement.isJsonPrimitive()) {
                            String rawString = rawElement.getAsString();
                            JsonObject raw = new Gson().fromJson(rawString, JsonObject.class);
                            String gameToken = raw.get("token").getAsString();
                            String uid = raw.get("uid").getAsString();
                            return getStokenByGameToken(uid, gameToken);
                        } else if (rawElement.isJsonObject()) {
                            JsonObject raw = rawElement.getAsJsonObject();
                            String gameToken = raw.get("token").getAsString();
                            String uid = raw.get("uid").getAsString();
                            if (frame != null)
                                frame.dispose();
                            return getStokenByGameToken(uid, gameToken);
                        } else {
                            throw new RuntimeException("raw 字段格式不正确");
                        }
                    default:
                        notifier.notifyListeners("未知的状态");
                        throw new RuntimeException("未知的状态");
                }
                TimeUnit.MILLISECONDS.sleep((int) (Math.random() * 500 + 1500));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return String[], [0] is stoken, [1] is mid, [2] is gameToken, [3] is stuid
     */
    private static String[] getStokenByGameToken(String stuid, String gameToken) {
        JsonObject json = new JsonObject();
        json.addProperty("account_id", Integer.parseInt(stuid));
        json.addProperty("game_token", gameToken);
        Map<String,Object> body = new HashMap<>(){{
            put("account_id", Integer.parseInt(stuid));
            put("game_token", gameToken);
        }};
        String ds = getDS2(json.toString(), SALT_6X,"");
        gameToken_headers.put("DS", ds);
        String response = tools.sendPostRequest("https://api-takumi.mihoyo.com/account/ma-cn-session/app/getTokenByGameToken", gameToken_headers,body);
        JsonObject result = new Gson().fromJson(response, JsonObject.class);
        if (result.get("retcode").getAsInt() != 0) {
            throw new RuntimeException("扫码获取stoken失败-getTokenByGameToken(RETCODE = " + result.get("retcode").getAsInt() + "),返回信息为：" + response);
        }
        JsonObject data = result.getAsJsonObject("data");
        JsonObject userInfo = data.getAsJsonObject("user_info");
        String mid = userInfo.get("mid").getAsString();
        JsonObject token = data.getAsJsonObject("token");
        String stoken = token.get("token").getAsString();
        tools.files.write("stoken", stoken);
        tools.files.write("mid", mid);
        tools.files.write("game_token", gameToken);
        tools.files.write("stuid", stuid);
        get_ltoken();
        return new String[]{stoken, mid, gameToken, stuid};
    }

    private static byte[] showQrCode(String qrUrl) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix bitMatrix = qrCodeWriter.encode(qrUrl, BarcodeFormat.QR_CODE, 300, 300, hints);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * 获取二维码图片，并返回二维码的数组（适合手机）
     * StatusNotifier notifier = new StatusNotifier();
     * notifier.addListener(status -> System.out.println("当前登录状态: " + status));
     * 执行phone(notifier)
     * notifier.removeListener(status -> System.out.println("当前登录状态: " + status));
     *
     * @return 二维码的byte[] --想要获取stoken需要使用getStoken()
     */
    public static byte[] phone(StatusNotifier notifier) {
        try {
            Tuple<String, String, String, String> result = getQrUrl();
            String qrUrl = result.first();
            String appId = result.second();
            String ticket = result.third();
            String device = result.fourth();
            Thread thread = new Thread(() -> tempStoken = checkLogin(appId, ticket, device, notifier));
            thread.start();
            return showQrCode(qrUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取phone方法的stoken
     *
     * @return String[], [0] stoken, [1] mid, [2] gameToken, [3] uid
     */
    public static String[] getStoken() {
        if (tempStoken != null)
            return tempStoken;
        else throw new RuntimeException("使用getStoken方法前请先调用phone方法");
    }

    /**
     * 获取二维码图片，并直接展示出来（适合电脑）
     * StatusNotifier notifier = new StatusNotifier();
     * notifier.addListener(status -> System.out.println("当前登录状态: " + status));
     * 执行computer(notifier)
     * notifier.removeListener(status -> System.out.println("当前登录状态: " + status));
     *
     * @return String[], [0] stoken, [1] mid, [2] gameToken, [3] uid
     */
    public static String[] computer(StatusNotifier notifier) {
        try {
            Tuple<String, String, String, String> result = getQrUrl();
            String qrUrl = result.first();
            String appId = result.second();
            String ticket = result.third();
            String device = result.fourth();
            byte[] imageBytes = showQrCode(qrUrl);
            BufferedImage qrImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            // 创建一个 JFrame 来显示二维码
            frame = new JFrame("QR Code");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(300, 300);
            JLabel label = new JLabel(new ImageIcon(qrImage));
            frame.getContentPane().add(label, BorderLayout.CENTER);
            frame.setVisible(true);
            return checkLogin(appId, ticket, device, notifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 自定义元组类
    private record Tuple<T, U, V, W>(T first, U second, V third, W fourth) {
    }
}
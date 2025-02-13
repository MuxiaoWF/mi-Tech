package com.muxiao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import static com.muxiao.fixed.*;

public class get_stoken {

    private static String rsaEncrypt(String message) {
        Objects.requireNonNull(message, "message");
        try {
            // 公钥字符串（Base64 编码）
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyString);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(spec);
            // 加密数据
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            // 编码为 Base64
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("RSA加密出错" + e);
        }
    }

    /**
     * 通过密码获取stoken和mid
     * 会自动保存在内置的json文件中
     *
     * @param account  账号
     * @param password 密码
     * @return String[], [0]=stoken,[1]=mid , [2]=stuid, [3]=login_ticket
     */
    public static String[] getStokenByPassword(String account, String password) {
        try {
            Map<String, Object> body = new HashMap<>() {{
                put("account", rsaEncrypt(account));
                put("password", rsaEncrypt(password));
            }};
            String response = tools.sendPostRequest(
                    "https://passport-api.mihoyo.com/account/ma-cn-passport/app/loginByPassword", password_headers, body);
            return handleResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("获取stoken失败\n" + e);
        }
    }

    //处理请求
    private static String[] handleResponse(String responseBody) {
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
        int retCode = jsonObject.get("retcode").getAsInt();
        if (retCode == 0) {
            JsonObject data = jsonObject.getAsJsonObject("data");
            JsonObject tokenData = data.getAsJsonObject("token");
            JsonObject userInfo = data.getAsJsonObject("user_info");
            String stoken = tokenData.get("token").getAsString();
            String mid = userInfo.get("mid").getAsString();
            String stuid = userInfo.get("aid").getAsString();
            String login_ticket = data.get("login_ticket").getAsString();
            tools.files.write("stoken", stoken);
            tools.files.write("mid", mid);
            tools.files.write("stuid", stuid);
            tools.files.write("login_ticket", login_ticket);
            get_stoken_qrcode.get_ltoken();
            return new String[]{stoken, mid ,stuid, login_ticket};
        } else {
            String message = jsonObject.get("message").getAsString();
            throw new RuntimeException("获取stoken失败(RETCODE),返回信息为：" + message);
        }
    }

    /**
     * 通过手机号和验证码获取stoken
     * 会自动保存在内置的json文件中
     * 验证码网址（不要直接登录，获取了验证码之后填在captcha这里）：<a href="https://user.miyoushe.com/login-platform/mobile.html#/login/captcha">米游社验证码登录</a>
     *
     * @param mobile  手机号
     * @param captcha 验证码
     * @return String[], [0]=stoken,[1]=mid,[2]=stuid, [3]=login_ticket
     */
    public static String[] getStokenByPhoneAndCaptcha(String mobile, String captcha) {
        try {
            Map<String, Object> body = new HashMap<>() {{
                put("area_code", rsaEncrypt("+86"));
                put("mobile", rsaEncrypt(mobile));
                put("action_type", "login_by_mobile_captcha");
                put("captcha", captcha);
            }};
            String response = tools.sendPostRequest(
                    "https://passport-api.mihoyo.com/account/ma-cn-passport/app/loginByMobileCaptcha", captcha_headers, body);
            return handleResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("获取stoken失败\n" + e);
        }
    }
}

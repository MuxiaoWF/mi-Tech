package com.muxiao.localCaptcha;

import com.muxiao.tools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
public class GeetestController extends tools {
    public static String gt;
    public static String challenge;
    private String latestValidate;
    private String geetest_seccode;
    private String geetest_challenge;
    public static tools.StatusNotifier statusNotifier = new tools.StatusNotifier();
    private final SecretKeySpec aesKey;

    @Autowired
    public GeetestController(@Value("${aes.key}") String key) {
        byte[] decodedKey = Base64.getDecoder().decode(key);
        this.aesKey = new SecretKeySpec(decodedKey, "AES");
    }

    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public String decrypt(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }
    @GetMapping("/init-geetest")
    public Map<String, Object> initGeetest() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("gt", gt);
        response.put("challenge", challenge);
        response.put("new_captcha", true);
        return response;
    }

    @PostMapping("/verify-geetest")
    public void verifyGeetest(@RequestBody Map<String, String> payload) {
        String geetestChallenge = payload.get("geetest_challenge");
        String geetestValidate = payload.get("geetest_validate");
        String geetestSeccode = payload.get("geetest_seccode");
        latestValidate = geetestValidate;
        geetest_seccode = geetestSeccode;
        geetest_challenge = geetestChallenge;
        statusNotifier.notifyListeners("true");
    }


    @GetMapping("/get-latest-validate")
    public Map<String, Object> getLatestValidate() {
        Map<String, Object> response = new HashMap<>();
        response.put("validate", latestValidate);
        response.put("seccode", geetest_seccode);
        response.put("challenge", geetest_challenge);
        return response;
    }
}

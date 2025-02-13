package com.muxiao;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.muxiao.localCaptcha.GeetestController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.muxiao.fixed.SALT_4X;
import static com.muxiao.get_stoken_qrcode.getDS2;
import static com.muxiao.localCaptcha.MySpringListener.started;
import static com.muxiao.tools.*;

@SpringBootApplication
public class start {

    public static String[] start_service1(Map<String,String > headers) {
        if (!started) {
            SpringApplication app = new SpringApplication(start.class);
            app.setLogStartupInfo(false);
            app.run();
            started = true;
        }
        String[] temp = get_gt_1(headers);
        GeetestController.gt = temp[0];
        GeetestController.challenge = temp[1];
        passChallengeRef = new AtomicReference<>();
        GeetestController.statusNotifier.addListener(status -> {
            if ("true".equals(status)) {
                passChallengeRef.set(getPassChallenge1(headers));
            }
        });
        while (passChallengeRef.get() == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread was interrupted", e);
            }
        }
        GeetestController.statusNotifier.removeAllListeners();
        return passChallengeRef.get();
    }
    public static AtomicReference<String[]> passChallengeRef;
    public static String[] start_service2(Map<String, String> headers) {
        if (!started) {
            SpringApplication app = new SpringApplication(start.class);
            app.setLogStartupInfo(false);
            app.run();
            started = true;
        }
        String[] temp = get_gt_2(headers);
        GeetestController.gt = temp[0];
        GeetestController.challenge = temp[1];
        passChallengeRef = new AtomicReference<>();
        GeetestController.statusNotifier.addListener(status -> {
            if ("true".equals(status)) {
                passChallengeRef.set(getPassChallenge2(headers));
            }
        });
        while (passChallengeRef.get() == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread was interrupted", e);
            }
        }
        return passChallengeRef.get();
    }

    public static String[] getPassChallenge1(Map<String,String> headers) {
        String local_url = "http://localhost:8080/get-latest-validate";
        String responses = sendGetRequest(local_url, headers, new HashMap<>());
        JsonObject response = JsonParser.parseString(responses).getAsJsonObject();
        String validate = response.get("validate").getAsString();
        String challenge = response.get("challenge").getAsString();
        String seccode = response.get("seccode").getAsString();
        GeetestController.statusNotifier.notifyListeners("false");
        if (validate != null) {
            Map<String, Object> body = new HashMap<>();
            body.put("geetest_challenge", challenge);
            body.put("geetest_seccode", seccode);
            body.put("geetest_validate", validate);
            String checkResponse = sendPostRequest("https://bbs-api.miyoushe.com/misc/api/verifyVerification", headers, body);
            JsonObject check = JsonParser.parseString(checkResponse).getAsJsonObject();
            if (check.get("retcode").getAsInt() == 0 && check.getAsJsonObject("data").get("challenge").getAsString() != null) {
                return new String[]{check.getAsJsonObject("data").get("challenge").getAsString(), validate};
            }
        }
        return null;
    }
    public static String[] getPassChallenge2(Map<String,String> headers) {
        String local_url = "http://localhost:8080/get-latest-validate";
        String responses = sendGetRequest(local_url, headers, new HashMap<>());
        JsonObject response = JsonParser.parseString(responses).getAsJsonObject();
        String validate = response.get("validate").getAsString();
        String challenge = response.get("challenge").getAsString();
        String seccode = response.get("seccode").getAsString();
        GeetestController.statusNotifier.notifyListeners("false");
        if (validate != null) {
            Map<String, Object> body = new HashMap<>();
            body.put("geetest_challenge", challenge);
            body.put("geetest_seccode", seccode);
            body.put("geetest_validate", validate);
            JsonObject json = new JsonObject();
            json.addProperty("geetest_challenge", challenge);
            json.addProperty("geetest_seccode", seccode);
            json.addProperty("geetest_validate", validate);
            headers.put("DS",getDS2(json.toString(),SALT_4X,""));
            String checkResponse = sendPostRequest("https://api-takumi-record.mihoyo.com/game_record/app/card/wapi/verifyVerification",headers, body);
            JsonObject check = JsonParser.parseString(checkResponse).getAsJsonObject();
            if (check.get("retcode").getAsInt() == 0 && check.getAsJsonObject("data").get("challenge").getAsString() != null) {
                return new String[]{check.getAsJsonObject("data").get("challenge").getAsString(), validate};
            }
        }
        return null;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

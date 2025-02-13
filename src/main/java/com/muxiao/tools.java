package com.muxiao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class tools {
    protected static String[] get_gt_1(Map<String, String> headers) {
        String response = sendGetRequest("https://bbs-api.miyoushe.com/misc/api/createVerification?is_high=true", headers, null);
        JsonObject data = JsonParser.parseString(response).getAsJsonObject();
        if (data.get("retcode").getAsInt() != 0) {
            throw new RuntimeException("获取验证码失败misc/api/createVerification" + response);
        }
        return new String[]{data.getAsJsonObject("data").get("gt").getAsString(), data.getAsJsonObject("data").get("challenge").getAsString()};
    }

    protected static String[] get_gt_2(Map<String, String> header) {
        String response = sendGetRequest("https://api-takumi-record.mihoyo.com/game_record/app/card/wapi/createVerification?is_high=true", header, null);
        JsonObject data = JsonParser.parseString(response).getAsJsonObject();
        if (data.get("retcode").getAsInt() != 0) {
            throw new RuntimeException("获取验证码失败card/wapi/createVerification" + response);
        }
        return new String[]{data.getAsJsonObject("data").get("gt").getAsString(), data.getAsJsonObject("data").get("challenge").getAsString()};
    }

    protected static String sendGetRequest(String urlStr, Map<String, String> headers, Map<String, String> params) {
        try {
            StringBuilder urlBuilder = new StringBuilder(urlStr);
            if (params != null) {
                urlBuilder.append("?");
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                urlBuilder.deleteCharAt(urlBuilder.length() - 1);
            }
            URL url = new URI(urlBuilder.toString()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setRequestProperty("Accept-Charset", "UTF-8");
            connection.setRequestProperty("Accept", "application/json; charset=utf-8");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            String contentEncoding = connection.getContentEncoding();
            InputStream inputStream;
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                inputStream = new GZIPInputStream(connection.getInputStream());
            } else {
                inputStream = connection.getInputStream();
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } catch (UnknownHostException e) {
            throw new RuntimeException("请检查是否联网");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static String sendPostRequest(String urlStr, Map<String, String> headers, Map<String, Object> body) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            Gson gson = new Gson();
            String jsonInputString = gson.toJson(body);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            String contentEncoding = connection.getContentEncoding();
            InputStream inputStream;
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                inputStream = new GZIPInputStream(connection.getInputStream());
            } else {
                inputStream = connection.getInputStream();
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } catch (UnknownHostException e) {
            throw new RuntimeException("请检查是否联网");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("请求的资源未找到: " + urlStr, e);
        } catch (IOException e) {
            throw new RuntimeException("IO 异常: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static String captcha(String gt, String challenge) {
        try {
            // 构建请求URL
            String urlString = String.format("http://127.0.0.1:9645/pass_nine?gt=%s&challenge=%s", gt, challenge);
            URL url = new URI(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                System.out.println(response);
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(response.toString(), JsonObject.class);
                JsonObject data = jsonObject.getAsJsonObject("data");
                if ("success".equals(data.get("result").getAsString())) {
                    return data.get("validate").getAsString();
                }
            } else {
                System.out.println("GET request not worked");
            }
        } catch (ConnectException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("验证验证码时出错" + e);
        }
        return null;
    }

    /**
     * 监听器接口，勿用
     */
    public interface StatusInterface {
        void onLoginStatusChanged(String status);
    }

    /**
     * 监听器类
     */
    public static class StatusNotifier {
        private final List<StatusInterface> listeners = new ArrayList<>();

        /**
         * 添加监听器
         *
         * @param listener 监听器
         */
        public void addListener(StatusInterface listener) {
            listeners.add(listener);
        }

        /**
         * 移除监听器
         *
         * @param listener 监听器
         */
        public void removeListener(StatusInterface listener) {
            listeners.remove(listener);
        }

        /**
         * 移除所有监听器
         */
        public void removeAllListeners() {
            listeners.clear();
        }

        public void notifyListeners(String status) {
            for (StatusInterface listener : listeners) {
                listener.onLoginStatusChanged(status);
            }
        }
    }

    public static class files {
        private static String user_name;

        // 从 JSON 文件读取并转换为 Map
        private static Map<String, String> readF(String fileName) {
            Gson gson = new Gson();
            File file = new File(fileName);
            if (!file.exists()) {
                return new HashMap<>();
            }
            try (FileReader reader = new FileReader(file)) {
                return gson.fromJson(reader, new TypeToken<Map<String, String>>() {
                }.getType());
            } catch (IOException e) {
                throw new RuntimeException("读取程序内保存的json文件出错" + e);
            }
        }

        protected static Map<String, String> read() {
            if (user_name != null)
                return readF(user_name + ".json");
            else
                throw new RuntimeException("请先设置用户");
        }

        protected static Map<String, String> read_global() {
            return readF("global.json");
        }

        protected static List<Map<String, String>> read(String game_id) {
            Gson gson = new Gson();
            File file = new File(user_name + ".json");
            if (!file.exists()) {
                return new ArrayList<>();
            }
            try (FileReader reader = new FileReader(file)) {
                Map<String, String> parsedData = gson.fromJson(reader, new TypeToken<Map<String, String>>() {
                }.getType());
                // 提取内层的 JSON 字符串并解析
                String user_str = parsedData.get(game_id + "_user");
                Type listType = new TypeToken<List<Map<String, String>>>() {
                }.getType();
                return gson.fromJson(user_str, listType);
            } catch (IOException e) {
                throw new RuntimeException("读取程序内保存的json文件出错" + e);
            }
        }

        // 将 Map 转换为 JSON 并保存
        protected static void write(Map<String, String> data, String file_name) {
            Gson gson = new Gson();
            String json = gson.toJson(data);
            try (FileWriter writer = new FileWriter(file_name)) {
                writer.write(json);
            } catch (IOException e) {
                throw new RuntimeException("保存json文件出错" + e);
            }
        }

        protected static void write(String key, String data) {
            Map<String, String> map = read();
            map.put(key, data);
            write(map, user_name + ".json");
        }

        private static void write_global(String key, String data) {
            Map<String, String> map = read_global();
            map.put(key, data);
            write(map, "global.json");
        }

        /**
         * 设置生成deviceID的命名空间
         *
         * @param data 随便给个URL字符串
         */
        public static void setDeviceNameSpace(String data) {
            write_global("device_name_space", data);
        }

        /**
         * 设置生成deviceID的名称
         *
         * @param data 随便给个字符串
         */
        public static void setDeviceName(String data) {
            write_global("device_name", data);
        }

        public static void addUser(String user_name) {
            Map<String, String> map = read_global();
            if (map.containsKey("user")) {
                String user = map.get("user");
                String[] users = user.split(",");
                if (Arrays.asList(users).contains(user_name)) {
                    throw new RuntimeException("用户名已存在");
                }
                write_global("user", user + "," + user_name);
            } else {
                write_global("user", user_name);
            }
        }

        public static String[] getUser() {
            Map<String, String> map = read_global();
            if (map.containsKey("user")) {
                String user = map.get("user");
                return user.split(",");
            } else {
                return new String[0];
            }
        }

        public static void setUser(String user_name) {
            files.user_name = user_name;
        }
    }
}

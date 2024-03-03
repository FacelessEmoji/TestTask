package test.task;

import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


public class CrptApi {
    private final RequestLimitTracker requestLimitTracker;
    private final HttpClient httpClient;
    private final JsonSerializer jsonSerializer;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimitTracker = new RequestLimitTracker(timeUnit, requestLimit);
        this.httpClient = new HttpClient();
        this.jsonSerializer = new JsonSerializer();
    }

    public void createDocument(CrptDocument document, String signature) {
        synchronized (requestLimitTracker) {
            if (requestLimitTracker.isLimitExceeded()) {
                System.out.println("Превышен лимит запросов. Запрос не может быть выполнен.");
                return;
            }
            requestLimitTracker.incrementRequestCount();
        }

        String documentJson = jsonSerializer.serialize(document);
        HttpResponse response = httpClient.post("https://ismp.crpt.ru/api/v3/lk/documents/create", documentJson, signature);

        if (response.getStatusCode() == 200) {
            System.out.println("Документ успешно создан.");
        } else {
            System.out.println("Ошибка при создании документа: " + response.getStatusMessage());
        }
    }

    private class RequestLimitTracker {
        private final TimeUnit timeUnit;
        private final int requestLimit;
        private int requestCount;
        private long lastResetTime;

        public RequestLimitTracker(TimeUnit timeUnit, int requestLimit) {
            this.timeUnit = timeUnit;
            this.requestLimit = requestLimit;
            this.requestCount = 0;
            this.lastResetTime = System.currentTimeMillis();
        }

        public synchronized void incrementRequestCount() {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastResetTime;

            if (elapsedTime > timeUnit.toMillis(1)) {
                requestCount = 0;
                lastResetTime = currentTime;
            }

            requestCount++;
        }

        public synchronized boolean isLimitExceeded() {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastResetTime;

            return elapsedTime <= timeUnit.toMillis(1) && requestCount >= requestLimit;
        }
    }

    public class HttpClient {
        public HttpResponse post(String url, String requestBody, String signature) {
            try {
                URL apiUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Signature", signature);
                connection.setDoOutput(true);
                connection.getOutputStream().write(requestBody.getBytes());

                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();
                StringBuilder responseBody = new StringBuilder();
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    responseBody.append(inputLine);
                }
                in.close();
                connection.disconnect();

                return new HttpResponse(responseCode, responseMessage, responseBody.toString());
            } catch (IOException e) {
                e.printStackTrace();
                return new HttpResponse(500, "Internal Server Error", "");
            }
        }
    }

    public class JsonSerializer {
        public String serialize(Object object) {
            if (object == null) {
                return "{}";
            }

            StringBuilder jsonBuilder = new StringBuilder("{");

            try {
                for (java.lang.reflect.Field field : object.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    Object value = field.get(object);

                    jsonBuilder.append("\"").append(fieldName).append("\":");

                    if (value == null) {
                        jsonBuilder.append("null");
                    } else if (value instanceof String) {
                        jsonBuilder.append("\"").append(value).append("\"");
                    } else {
                        jsonBuilder.append(value);
                    }

                    jsonBuilder.append(",");
                }

                if (jsonBuilder.charAt(jsonBuilder.length() - 1) == ',') {
                    jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            jsonBuilder.append("}");

            return jsonBuilder.toString();
        }

        public <T> T deserialize(String json, Class<T> type) {
            if (json == null || json.isEmpty()) {
                return null;
            }

            try {
                T instance = type.getDeclaredConstructor().newInstance();
                json = json.replaceAll("[{}\"]", "");
                String[] keyValuePairs = json.split(",");

                for (String pair : keyValuePairs) {
                    String[] entry = pair.split(":", 2);
                    String key = entry[0].trim();
                    String value = entry[1].trim();

                    java.lang.reflect.Field field = type.getDeclaredField(key);
                    field.setAccessible(true);

                    if (field.getType() == String.class) {
                        field.set(instance, value);
                    } else if (field.getType() == int.class || field.getType() == Integer.class) {
                        field.set(instance, Integer.parseInt(value));
                    } else if (field.getType() == long.class || field.getType() == Long.class) {
                        field.set(instance, Long.parseLong(value));
                    }
                }

                return instance;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public static class CrptDocument {
        private String participantInn;
        private String docId;
        private String docStatus;

        public CrptDocument(String participantInn, String docId, String docStatus) {
            this.participantInn = participantInn;
            this.docId = docId;
            this.docStatus = docStatus;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }
    }

    private class HttpResponse {
        private int statusCode;
        private String statusMessage;
        private String body;

        public HttpResponse(int statusCode, String statusMessage, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public String getBody() {
            return body;
        }
    }
}


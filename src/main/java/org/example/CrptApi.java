package org.example;

import com.google.gson.Gson;
import lombok.*;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private static final String TOKEN = "my_token";

    private final int requestLimit;
    private final TimeUnit timeUnit;

    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final Lock lock = new ReentrantLock();
    private final Condition resetCounterCondition = lock.newCondition();
    private Date lastResetTime = new Date();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    private boolean canMakeRequest() {
        lock.lock();
        if (requestCounter.get() >= requestLimit) {
            Date currentTime = new Date();
            long elapsedTime = currentTime.getTime() - lastResetTime.getTime();
            if (elapsedTime >= timeUnit.toMillis(1)) {
                requestCounter.set(0);
                lastResetTime = currentTime;
            } else {
                return false;
            }
        }
        lock.unlock();
        return true;
    }

    private void incrementRequestCounter() {
        lock.lock();
        int currentCount = requestCounter.incrementAndGet();
        if (currentCount >= requestLimit) {
            resetCounterCondition.signalAll();
        }
        lock.unlock();
    }

    public CustomResponse createDocument(Document document, Product.ProductGroup productGroup) {
        CustomResponse taskResponse = null;
        if (canMakeRequest()) {
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

                String BASE_URL = "https://ismp.crpt.ru";
                String CREATE_DOC_URL = "/api/v3/lk/documents/commissioning/contract/create";
                HttpPost httpPost = new HttpPost(BASE_URL + CREATE_DOC_URL);
                httpPost.addHeader("Authorization", "Bearer " + TOKEN);
                httpPost.addHeader("Content-Type", "application/json");

                Gson gson = new Gson();
                String documentJson = gson.toJson(document);

                String requestBody = String.format("{\"product_document\": %s, \"product_group\": \"%s\", \"document_format\": \"MANUAL\", \"type\": \"LP_INTRODUCE_GOODS\"}",
                        documentJson, productGroup.getValue());

                httpPost.setEntity(new StringEntity(requestBody));

                CloseableHttpResponse response = httpClient.execute(httpPost);
                String responseJson = EntityUtils.toString(response.getEntity());

                if (response.getCode() == 200) {
                    incrementRequestCounter();
                    taskResponse = new CustomResponse(response.getCode(), "Документ успешно создан");
                } else {
                    taskResponse = new CustomResponse(response.getCode(), "Ошибка при создании документа: " + responseJson);
                }
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        } else {
            taskResponse = new CustomResponse(null, "Достигнут лимит запросов, попробуйте позже");
        }

        return taskResponse;
    }

    @Getter
    @Setter
    public static class Document {
        @NonNull
        private String doc_id;
        @NonNull
        private String doc_status;
        @NonNull
        private String doc_type;
        private String importRequest;
        @NonNull
        private String owner_inn;
        @NonNull
        private String participant_inn;
        @NonNull
        private String producer_inn;
        @NonNull
        private String production_date;
        @NonNull
        private String production_type;
        @NonNull
        private String reg_date;
        private String reg_number;
        private String description;
        private ArrayList<Product> products;

        public Document(String doc_id, String doc_status, String doc_type, String owner_inn, String participant_inn,
                        String producer_inn, String production_date, String production_type, String reg_date) {
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.reg_date = reg_date;
        }
    }

    @Getter
    @Setter
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        @NonNull
        private String owner_inn;
        @NonNull
        private String producer_inn;
        private String production_date;
        @NonNull
        private String tnved_code;
        private String uit_code;
        private String uitu_code;


        public Product(String owner_inn, String producer_inn, String tnved_code, String uitOrUitu) {
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.tnved_code = tnved_code;
            this.uit_code = uitOrUitu;
            this.uitu_code = uitOrUitu;
        }

         @Getter
         enum ProductGroup {
            CLOTHES("clothes"),
            SHOES("shoes"),
            TOBACCO("tobacco"),
            PERFUMERY("perfumery"),
            TIRES("tires"),
            ELECTRONICS("electronics"),
            PHARMA("pharma"),
            MILK("milk"),
            BICYCLE("bicycle"),
            WHEELCHAIRS("wheelchairs");

            private final String value;

            ProductGroup(String value) {
                this.value = value;
            }

         }
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class CustomResponse{
        Integer statusCode;
        String responseBody;
    }
}

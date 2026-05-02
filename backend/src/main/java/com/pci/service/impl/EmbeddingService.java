package com.pci.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class EmbeddingService {

    @Value("${zhipu.api-key}")
    private String apiKey;

    @Value("${zhipu.embedding-url}")
    private String embeddingUrl;

    @Value("${zhipu.embedding-model}")
    private String embeddingModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 将文本转为向量，失败返回 null
     */
    public float[] embed(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", embeddingModel);
            body.put("input", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(embeddingUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embedding = root.path("data").get(0).path("embedding");

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            log.error("[Embedding] failed for text: {}", text.substring(0, Math.min(50, text.length())), e);
            return null;
        }
    }
}

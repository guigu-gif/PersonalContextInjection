package com.pci.service.impl;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

@Slf4j
@Service
public class QdrantService {

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.port}")
    private int port;

    @Value("${qdrant.collection-memo}")
    private String collectionMemo;

    @Value("${zhipu.embedding-dim}")
    private int embeddingDim;

    private QdrantClient client;

    @PostConstruct
    public void init() {
        try {
            client = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
            ensureCollection(collectionMemo);
            log.info("[Qdrant] connected, collection={}", collectionMemo);
        } catch (Exception e) {
            log.warn("[Qdrant] init failed, semantic search will be unavailable: {}", e.getMessage());
        }
    }

    private void ensureCollection(String name) {
        try {
            boolean exists = client.listCollectionsAsync().get()
                    .stream().anyMatch(c -> c.equals(name));
            if (!exists) {
                client.createCollectionAsync(name,
                        VectorParams.newBuilder()
                                .setSize(embeddingDim)
                                .setDistance(Distance.Cosine)
                                .build()
                ).get();
                log.info("[Qdrant] created collection: {}", name);
            }
        } catch (Exception e) {
            log.warn("[Qdrant] ensureCollection failed: {}", e.getMessage());
        }
    }

    /**
     * 备忘录向量入库（upsert）
     */
    public void upsertMemo(Long memoId, Long userId, String title, String content, float[] vector) {
        if (client == null || vector == null) return;
        try {
            List<Float> vec = new ArrayList<>(vector.length);
            for (float v : vector) vec.add(v);

            PointStruct point = PointStruct.newBuilder()
                    .setId(id(memoId))
                    .setVectors(vectors(vec))
                    .putPayload("userId", value(userId))
                    .putPayload("title",  value(title != null ? title : ""))
                    .putPayload("content", value(content != null ? content : ""))
                    .build();

            client.upsertAsync(collectionMemo, Collections.singletonList(point)).get();
        } catch (Exception e) {
            log.error("[Qdrant] upsert failed memoId={}: {}", memoId, e.getMessage());
        }
    }

    /**
     * 删除备忘录向量
     */
    public void deleteMemo(Long memoId) {
        if (client == null) return;
        try {
            client.deleteAsync(collectionMemo,
                    Collections.singletonList(id(memoId))).get();
        } catch (Exception e) {
            log.error("[Qdrant] delete failed memoId={}: {}", memoId, e.getMessage());
        }
    }

    /**
     * 语义搜索，返回匹配的 memoId 列表（按相似度降序）
     */
    public List<Long> searchMemos(Long userId, float[] queryVector, int topK) {
        if (client == null || queryVector == null) return Collections.emptyList();
        try {
            List<Float> vec = new ArrayList<>(queryVector.length);
            for (float v : queryVector) vec.add(v);

            Filter userFilter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("userId")
                                    .setMatch(Match.newBuilder().setInteger(userId).build())
                                    .build())
                            .build())
                    .build();

            List<ScoredPoint> results = client.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collectionMemo)
                            .addAllVector(vec)
                            .setFilter(userFilter)
                            .setLimit(topK)
                            .setWithPayload(enable(true))
                            .build()
            ).get();

            return results.stream()
                    .map(p -> p.getId().getNum())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[Qdrant] search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}

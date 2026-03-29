package com.ecommerce.search.indexing;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexManagementService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index.alias:products}")
    private String indexAlias;

    @Value("${elasticsearch.index.name-prefix:products-v}")
    private String indexPrefix;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            String currentIndex = getCurrentIndexName();

            if (!indexExists(currentIndex)) {
                log.info("Creating Elasticsearch index: {}", currentIndex);
                createIndex(currentIndex);
                createAlias(currentIndex);
                log.info("Index {} created with alias {}", currentIndex, indexAlias);
            } else {
                log.info("Elasticsearch index {} already exists", currentIndex);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch index", e);
        }
    }

    private String getCurrentIndexName() {
        return indexPrefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public boolean indexExists(String indexName) throws IOException {
        return elasticsearchClient.indices().exists(r -> r.index(indexName)).value();
    }

    public void createIndex(String indexName) throws IOException {
        InputStream settingsStream = new ClassPathResource("elasticsearch/product-settings.json").getInputStream();
        InputStream mappingStream = new ClassPathResource("elasticsearch/product-mapping.json").getInputStream();
        elasticsearchClient.indices().create(r -> r.index(indexName).settings(s -> s.withJson(settingsStream)).mappings(m -> m.withJson(mappingStream)));
    }

    public void createAlias(String indexName) throws IOException {
        elasticsearchClient.indices().putAlias(r -> r.index(indexName).name(indexAlias));
    }

    public void swapAlias(String oldIndex, String newIndex) throws IOException {
        elasticsearchClient.indices().updateAliases(r -> r.actions(a -> a.remove(rem -> rem.index(oldIndex).alias(indexAlias))).actions(a -> a.add(add -> add.index(newIndex).alias(indexAlias))));
    }
}

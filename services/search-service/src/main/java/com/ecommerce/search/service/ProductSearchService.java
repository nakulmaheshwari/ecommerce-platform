package com.ecommerce.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.*;
import co.elastic.clients.json.JsonData;
import com.ecommerce.search.api.dto.*;
import com.ecommerce.search.document.ProductDocument;
import com.ecommerce.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final ProductSearchRepository searchRepository;

    public com.ecommerce.search.api.dto.SearchResponse search(com.ecommerce.search.api.dto.SearchRequest request) throws IOException {
        long startMs = System.currentTimeMillis();
        co.elastic.clients.elasticsearch.core.SearchRequest.Builder esRequest = buildSearchRequest(request);
        co.elastic.clients.elasticsearch.core.SearchResponse<ProductDocument> esResponse =
            elasticsearchClient.search(esRequest.build(), ProductDocument.class);
        long tookMs = System.currentTimeMillis() - startMs;
        return buildSearchResponse(esResponse, request, tookMs);
    }

    private co.elastic.clients.elasticsearch.core.SearchRequest.Builder buildSearchRequest(com.ecommerce.search.api.dto.SearchRequest request) {
        Query mainQuery = buildMainQuery(request);
        List<SortOptions> sorts = buildSort(request.sortBy());
        Map<String, Aggregation> aggregations = buildAggregations();

        return new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index("products")
            .query(mainQuery)
            .sort(sorts)
            .aggregations(aggregations)
            .from(request.page() * request.size())
            .size(request.size())
            .highlight(h -> h
                .fields("name", f -> f.numberOfFragments(0))
                .fields("description", f -> f.numberOfFragments(1).fragmentSize(150))
                .preTags("<em>")
                .postTags("</em>")
            )
            .source(s -> s.filter(f -> f.includes(
                "productId", "sku", "name", "brand", "categoryName",
                "categorySlug", "pricePaise", "priceRupees", "mrpPaise",
                "discountPercent", "averageRating", "totalReviews",
                "inStock", "isDigital", "primaryImageUrl", "tags", "status"
            )));
    }

    private Query buildMainQuery(com.ecommerce.search.api.dto.SearchRequest request) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.term(t -> t.field("status").value("ACTIVE"))));

        if (request.categoryId() != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("categoryId").value(request.categoryId()))));
        }

        if (request.brands() != null && !request.brands().isEmpty()) {
            List<FieldValue> brandValues = request.brands().stream().map(FieldValue::of).collect(Collectors.toList());
            filters.add(Query.of(q -> q.terms(t -> t.field("brand.keyword").terms(tv -> tv.value(brandValues)))));
        }

        if (request.minPrice() != null || request.maxPrice() != null) {
            filters.add(Query.of(q -> q.range(r -> {
                r.field("pricePaise");
                if (request.minPrice() != null) r.gte(JsonData.of(request.minPrice()));
                if (request.maxPrice() != null) r.lte(JsonData.of(request.maxPrice()));
                return r;
            })));
        }

        if (request.minRating() != null) {
            filters.add(Query.of(q -> q.range(r -> r.field("averageRating").gte(JsonData.of(request.minRating())))));
        }

        if (Boolean.TRUE.equals(request.inStockOnly())) {
            filters.add(Query.of(q -> q.term(t -> t.field("inStock").value(true))));
        }

        if (request.tags() != null && !request.tags().isEmpty()) {
            List<FieldValue> tagValues = request.tags().stream().map(FieldValue::of).collect(Collectors.toList());
            filters.add(Query.of(q -> q.terms(t -> t.field("tags").terms(tv -> tv.value(tagValues)))));
        }

        if (request.q() != null && !request.q().isBlank()) {
            return buildTextSearchQuery(request.q(), filters);
        } else {
            return Query.of(q -> q.bool(b -> b.filter(filters)));
        }
    }

    private Query buildTextSearchQuery(String q, List<Query> filters) {
        Query multiMatch = Query.of(mq -> mq.multiMatch(mm -> mm
            .query(q)
            .fields("name^3", "brand^2", "categoryName^1.5", "description")
            .type(TextQueryType.BestFields)
            .fuzziness("AUTO")
            .prefixLength(1)
            .maxExpansions(50)
            .minimumShouldMatch("75%")
        ));

        Query autocompleteBoost = Query.of(mq -> mq.match(m -> m.field("name.autocomplete").query(q).boost(1.5f)));

        return Query.of(q2 -> q2.functionScore(fs -> fs
            .query(mq -> mq.bool(b -> b.must(multiMatch).should(autocompleteBoost).filter(filters)))
            .functions(f -> f.fieldValueFactor(fvf -> fvf.field("popularityScore").factor(0.1).modifier(FieldValueFactorModifier.Log1p).missing(0.0)))
            .boostMode(FunctionBoostMode.Multiply)
            .maxBoost(3.0)
        ));
    }

    private List<SortOptions> buildSort(String sortBy) {
        return switch (sortBy.toUpperCase()) {
            case "PRICE_ASC" -> List.of(SortOptions.of(s -> s.field(f -> f.field("pricePaise").order(SortOrder.Asc))));
            case "PRICE_DESC" -> List.of(SortOptions.of(s -> s.field(f -> f.field("pricePaise").order(SortOrder.Desc))));
            case "RATING" -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("averageRating").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("totalReviews").order(SortOrder.Desc)))
            );
            case "NEWEST" -> List.of(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            case "DISCOUNT" -> List.of(SortOptions.of(s -> s.field(f -> f.field("discountPercent").order(SortOrder.Desc))));
            case "POPULARITY" -> List.of(SortOptions.of(s -> s.field(f -> f.field("popularityScore").order(SortOrder.Desc))));
            default -> List.of(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        };
    }

    private Map<String, Aggregation> buildAggregations() {
        Map<String, Aggregation> aggs = new LinkedHashMap<>();
        aggs.put("brands", Aggregation.of(a -> a.terms(t -> t.field("brand.keyword").size(20))));
        aggs.put("categories", Aggregation.of(a -> a.terms(t -> t.field("categoryName.keyword").size(15))));
        aggs.put("price_ranges", Aggregation.of(a -> a.range(r -> r.field("pricePaise").ranges(
            AggregationRange.of(rng -> rng.to("50000")),
            AggregationRange.of(rng -> rng.from("50000").to("100000")),
            AggregationRange.of(rng -> rng.from("100000").to("500000")),
            AggregationRange.of(rng -> rng.from("500000").to("1500000")),
            AggregationRange.of(rng -> rng.from("1500000"))
        ))));
        aggs.put("ratings", Aggregation.of(a -> a.range(r -> r.field("averageRating").ranges(
            AggregationRange.of(rng -> rng.from("4.0").key("4_and_above")),
            AggregationRange.of(rng -> rng.from("3.0").to("4.0").key("3_to_4")),
            AggregationRange.of(rng -> rng.from("2.0").to("3.0").key("2_to_3"))
        ))));
        aggs.put("availability", Aggregation.of(a -> a.terms(t -> t.field("inStock").size(2))));
        return aggs;
    }

    private com.ecommerce.search.api.dto.SearchResponse buildSearchResponse(co.elastic.clients.elasticsearch.core.SearchResponse<ProductDocument> esResponse, com.ecommerce.search.api.dto.SearchRequest request, long tookMs) {
        List<com.ecommerce.search.api.dto.SearchResponse.ProductSearchResult> hits = esResponse.hits().hits().stream().map(hit -> {
            ProductDocument doc = hit.source();
            return new com.ecommerce.search.api.dto.SearchResponse.ProductSearchResult(
                doc.getProductId(), doc.getSku(), doc.getName(), doc.getBrand(), doc.getCategoryName(),
                doc.getCategorySlug(), doc.getPricePaise() != null ? doc.getPricePaise() : 0L,
                doc.getPriceRupees() != null ? doc.getPriceRupees() : 0.0,
                doc.getMrpPaise() != null ? doc.getMrpPaise() : 0L,
                doc.getDiscountPercent() != null ? doc.getDiscountPercent() : 0,
                doc.getAverageRating() != null ? doc.getAverageRating() : 0.0,
                doc.getTotalReviews() != null ? doc.getTotalReviews() : 0,
                Boolean.TRUE.equals(doc.getInStock()), Boolean.TRUE.equals(doc.getIsDigital()),
                doc.getPrimaryImageUrl(), hit.score() != null ? hit.score().floatValue() : 0f, doc.getTags()
            );
        }).collect(Collectors.toList());

        Map<String, List<com.ecommerce.search.api.dto.SearchResponse.FacetEntry>> facets = new LinkedHashMap<>();
        var aggs = esResponse.aggregations();
        if (aggs.containsKey("brands")) {
            facets.put("brands", aggs.get("brands").sterms().buckets().array().stream()
                .map(b -> new com.ecommerce.search.api.dto.SearchResponse.FacetEntry(b.key().stringValue(), b.docCount())).collect(Collectors.toList()));
        }
        if (aggs.containsKey("categories")) {
            facets.put("categories", aggs.get("categories").sterms().buckets().array().stream()
                .map(b -> new com.ecommerce.search.api.dto.SearchResponse.FacetEntry(b.key().stringValue(), b.docCount())).collect(Collectors.toList()));
        }

        long totalHits = esResponse.hits().total().value();
        int totalPages = (int) Math.ceil((double) totalHits / request.size());
        return new com.ecommerce.search.api.dto.SearchResponse(hits, facets, totalHits, request.page(), request.size(), totalPages, request.page() < totalPages - 1, tookMs, List.of());
    }

    public AutocompleteResponse autocomplete(String prefix) throws IOException {
        if (prefix == null || prefix.length() < 2) return new AutocompleteResponse(List.of(), List.of(), List.of());
        co.elastic.clients.elasticsearch.core.SearchResponse<ProductDocument> response = elasticsearchClient.search(s -> s
            .index("products")
            .query(q -> q.bool(b -> b.must(mq -> mq.match(m -> m.field("name.autocomplete").query(prefix)))
                .filter(f -> f.term(t -> t.field("status").value("ACTIVE")))
                .filter(f -> f.term(t -> t.field("inStock").value(true)))))
            .aggregations("brands", a -> a.terms(t -> t.field("brand.keyword").size(5)))
            .aggregations("categories", a -> a.terms(t -> t.field("categoryName.keyword").size(5)))
            .size(5), ProductDocument.class);

        List<String> suggestions = response.hits().hits().stream().map(h -> h.source().getName()).distinct().limit(5).collect(Collectors.toList());
        List<String> categories = response.aggregations().get("categories").sterms().buckets().array().stream().map(b -> b.key().stringValue()).collect(Collectors.toList());
        List<String> brands = response.aggregations().get("brands").sterms().buckets().array().stream().map(b -> b.key().stringValue()).collect(Collectors.toList());
        return new AutocompleteResponse(suggestions, categories, brands);
    }

    public void indexProduct(IndexProductRequest request) {
        double popularityScore = computePopularityScore(request.totalReviews(), request.averageRating());
        ProductDocument doc = ProductDocument.builder()
            .id(request.productId()).productId(request.productId()).sku(request.sku()).name(request.name())
            .nameAutocomplete(request.name()).description(request.description()).brand(request.brand())
            .categoryId(request.categoryId()).categoryName(request.categoryName()).categorySlug(request.categorySlug())
            .pricePaise(request.pricePaise()).priceRupees(request.pricePaise() != null ? request.pricePaise() / 100.0 : null)
            .mrpPaise(request.mrpPaise()).discountPercent(request.discountPercent()).status(request.status())
            .averageRating(request.averageRating()).totalReviews(request.totalReviews()).tags(request.tags())
            .attributes(request.attributes()).primaryImageUrl(request.primaryImageUrl())
            .inStock(request.inStock()).stockQuantity(request.stockQuantity()).isDigital(request.isDigital())
            .popularityScore(popularityScore).indexedAt(Instant.now()).updatedAt(Instant.now()).build();
        searchRepository.save(doc);
    }

    public void updateProductAvailability(String productId, boolean inStock, int stockQuantity) throws IOException {
        elasticsearchClient.update(u -> u.index("products").id(productId).doc(Map.of("inStock", inStock, "stockQuantity", stockQuantity, "updatedAt", Instant.now().toString())), ProductDocument.class);
    }

    public void removeProduct(String productId) {
        searchRepository.deleteByProductId(productId);
    }

    public void bulkIndex(List<IndexProductRequest> requests) throws IOException {
        if (requests.isEmpty()) return;
        var bulkRequest = new BulkRequest.Builder();
        for (IndexProductRequest req : requests) {
            double score = computePopularityScore(req.totalReviews(), req.averageRating());
            ProductDocument doc = ProductDocument.builder()
                .id(req.productId()).productId(req.productId()).sku(req.sku()).name(req.name()).nameAutocomplete(req.name())
                .description(req.description()).brand(req.brand()).categoryId(req.categoryId()).categoryName(req.categoryName())
                .pricePaise(req.pricePaise()).priceRupees(req.pricePaise() != null ? req.pricePaise() / 100.0 : null)
                .status(req.status()).averageRating(req.averageRating()).totalReviews(req.totalReviews())
                .inStock(req.inStock()).popularityScore(score).indexedAt(Instant.now()).build();
            bulkRequest.operations(op -> op.index(idx -> idx.index("products").id(req.productId()).document(doc)));
        }
        elasticsearchClient.bulk(bulkRequest.build());
    }

    private double computePopularityScore(Integer totalReviews, Double averageRating) {
        double reviews = totalReviews != null ? totalReviews : 0;
        double rating = averageRating != null ? averageRating : 0;
        return 1.0 + (Math.log1p(reviews) * 1.5) + (rating * 2.0);
    }
}

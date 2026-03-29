package com.ecommerce.search.api;

import com.ecommerce.search.api.dto.*;
import com.ecommerce.search.service.ProductSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final ProductSearchService searchService;

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) List<String> brands,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) throws IOException {
        SearchRequest request = new SearchRequest(q, categoryId, brands, minPrice, maxPrice, minRating, inStockOnly, tags, sortBy, page, size);
        return ResponseEntity.ok(searchService.search(request));
    }

    @GetMapping("/search/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(@RequestParam String q) throws IOException {
        return ResponseEntity.ok(searchService.autocomplete(q));
    }

    @PostMapping("/admin/search/index")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> indexProduct(@Valid @RequestBody IndexProductRequest request) {
        searchService.indexProduct(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/admin/search/bulk-index")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> bulkIndex(@RequestBody List<IndexProductRequest> requests) throws IOException {
        searchService.bulkIndex(requests);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/admin/search/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeProduct(@PathVariable String productId) {
        searchService.removeProduct(productId);
        return ResponseEntity.noContent().build();
    }
}

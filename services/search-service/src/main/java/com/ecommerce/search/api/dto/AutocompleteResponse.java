package com.ecommerce.search.api.dto;

import java.util.List;

public record AutocompleteResponse(
    List<String> suggestions,
    List<String> categories,
    List<String> brands
) {}

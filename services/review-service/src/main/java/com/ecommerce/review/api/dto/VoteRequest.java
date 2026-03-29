package com.ecommerce.review.api.dto;

import jakarta.validation.constraints.NotNull;

public record VoteRequest(@NotNull Boolean helpful) {}

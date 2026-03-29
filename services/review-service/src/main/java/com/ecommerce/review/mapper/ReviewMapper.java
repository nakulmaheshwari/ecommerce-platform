package com.ecommerce.review.mapper;

import com.ecommerce.review.api.dto.ReviewAggregateResponse;
import com.ecommerce.review.api.dto.ReviewResponse;
import com.ecommerce.review.domain.Review;
import com.ecommerce.review.domain.ReviewAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "status",
             expression = "java(review.getStatus().name())")
    @Mapping(target = "helpfulnessRatio",
             expression = "java(review.helpfulnessRatio())")
    ReviewResponse toResponse(Review review);

    List<ReviewResponse> toResponseList(List<Review> reviews);

    @Mapping(target = "rating1Percent",
             expression = "java(aggregate.ratingPercent(1))")
    @Mapping(target = "rating2Percent",
             expression = "java(aggregate.ratingPercent(2))")
    @Mapping(target = "rating3Percent",
             expression = "java(aggregate.ratingPercent(3))")
    @Mapping(target = "rating4Percent",
             expression = "java(aggregate.ratingPercent(4))")
    @Mapping(target = "rating5Percent",
             expression = "java(aggregate.ratingPercent(5))")
    ReviewAggregateResponse toAggregateResponse(ReviewAggregate aggregate);
}

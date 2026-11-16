package com.duong.ecommerce.product.mapper;

import com.duong.ecommerce.product.entity.Product;
import com.duong.ecommerce.product.payload.response.ProductPurchaseResponse;
import com.duong.ecommerce.product.payload.request.ProductRequest;
import com.duong.ecommerce.product.payload.response.ProductResponse;

import com.duong.ecommerce.product.category.entity.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {

    @Mapping(target = "category", source = "categoryId")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Product toProduct(@Valid ProductRequest request);

    // Preserve the existing requirement that a response product has a category.
    @Mapping(target = "categoryId", expression = "java(product.getCategory().getId())")
    @Mapping(target = "categoryName", expression = "java(product.getCategory().getName())")
    @Mapping(target = "categoryDescription", expression = "java(product.getCategory().getDescription())")
    ProductResponse toProductResponse(Product product);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "quantity", source = "quantity")
    ProductPurchaseResponse toProductPurchaseResponse(
            Product product, @NotNull(message = "Quantity is mandatory") double quantity);

    default Category toCategory(Integer categoryId) {
        // Even a null ID previously produced a category reference, rather than null.
        return Category.builder().id(categoryId).build();
    }

    @BeforeMapping
    default void requireRequest(ProductRequest request) {
        Objects.requireNonNull(request, "request");
    }

    @BeforeMapping
    default void requireProduct(Product product) {
        Objects.requireNonNull(product, "product");
    }
}

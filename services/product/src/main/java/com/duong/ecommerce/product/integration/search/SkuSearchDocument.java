package com.duong.ecommerce.product.integration.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import java.math.BigDecimal;
import java.util.List;

@Document(indexName = "products_v2", createIndex = false)
public record SkuSearchDocument(
        @Id @Field(type = FieldType.Keyword) String skuId,
        @Field(type = FieldType.Keyword) String spuId,
        @Field(type = FieldType.Keyword) String sku,
        @Field(type = FieldType.Keyword) String slug,
        @Field(type = FieldType.Text) String skuTitle,
        @Field(type = FieldType.Text) String description,
        @Field(type = FieldType.Scaled_Float, scalingFactor = 100) BigDecimal skuPrice,
        @Field(type = FieldType.Integer) int stockQuantity,
        @Field(type = FieldType.Boolean) boolean hasStock,
        @Field(type = FieldType.Nested) List<SkuAttributeDocument> attrs
) { }

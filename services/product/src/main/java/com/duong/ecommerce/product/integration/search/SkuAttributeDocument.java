package com.duong.ecommerce.product.integration.search;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
public record SkuAttributeDocument(
        @Field(type = FieldType.Keyword) String attrId,
        @Field(type = FieldType.Keyword) String attrName,
        @Field(type = FieldType.Keyword) String attrValueId,
        @Field(type = FieldType.Keyword) String attrValue
) { }

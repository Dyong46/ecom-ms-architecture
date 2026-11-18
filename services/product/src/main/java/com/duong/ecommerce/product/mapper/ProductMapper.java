package com.duong.ecommerce.product.mapper;
import com.duong.ecommerce.product.entity.*;
import com.duong.ecommerce.product.payload.response.*;
import com.duong.ecommerce.product.integration.search.SkuSearchDocument;
import com.duong.ecommerce.product.integration.search.SkuAttributeDocument;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {
    @Mapping(target = "skuId", source = "id")
    @Mapping(target = "spuId", source = "product.id")
    @Mapping(target = "slug", source = "product.slug")
    @Mapping(target = "skuTitle", source = "product.name")
    @Mapping(target = "description", source = "product.description")
    @Mapping(target = "skuPrice", source = "price")
    @Mapping(target = "hasStock", expression = "java(variant.getStockQuantity() > 0)")
    @Mapping(target = "attrs", source = "options")
    SkuSearchDocument toSearchDocument(ProductVariant variant);

    @Mapping(target = "attrId", source = "optionValue.option.id")
    @Mapping(target = "attrName", source = "optionValue.option.name")
    @Mapping(target = "attrValueId", source = "optionValue.id")
    @Mapping(target = "attrValue", source = "optionValue.value")
    SkuAttributeDocument toSearchAttribute(ProductVariantOptionValue selection);
    ProductResponse toProductResponse(Product product);
    VariantResponse toVariantResponse(ProductVariant variant);
    @Mapping(target = "optionId", source = "optionValue.option.id")
    @Mapping(target = "optionName", source = "optionValue.option.name")
    @Mapping(target = "optionValueId", source = "optionValue.id")
    @Mapping(target = "value", source = "optionValue.value")
    VariantOptionResponse toVariantOptionResponse(ProductVariantOptionValue selection);
    OptionResponse toOptionResponse(Option option);
    OptionValueResponse toOptionValueResponse(OptionValue value);
}

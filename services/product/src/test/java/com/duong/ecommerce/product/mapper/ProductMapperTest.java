package com.duong.ecommerce.product.mapper;

import com.duong.ecommerce.product.entity.Product;
import com.duong.ecommerce.product.payload.response.ProductPurchaseResponse;
import com.duong.ecommerce.product.payload.request.ProductRequest;
import com.duong.ecommerce.product.payload.response.ProductResponse;

import com.duong.ecommerce.product.category.entity.Category;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@SpringJUnitConfig(ProductMapperImpl.class)
class ProductMapperTest {

    @Autowired
    private ProductMapper mapper;

    @Test
    void mapsRequestToProductWithCategoryReferenceAndLeavesAuditFieldsUnset() {
        var request = new ProductRequest(7, "Keyboard", "Mechanical", 12.5,
                new BigDecimal("99.90"), 3);

        var product = mapper.toProduct(request);

        assertThat(product).extracting(Product::getId, Product::getName, Product::getDescription,
                        Product::getAvailableQuantity, Product::getPrice)
                .containsExactly(7, "Keyboard", "Mechanical", 12.5, new BigDecimal("99.90"));
        assertThat(product.getCategory()).extracting(Category::getId, Category::getName,
                        Category::getDescription, Category::getProducts)
                .containsExactly(3, null, null, null);
        assertThat(product).extracting(Product::getCreatedBy, Product::getUpdatedBy,
                        Product::getCreatedAt, Product::getUpdatedAt, Product::getDeletedAt)
                .containsOnlyNulls();
    }

    @Test
    void preservesCategoryReferenceWhenRequestCategoryIdIsNull() {
        var product = mapper.toProduct(new ProductRequest(null, "Keyboard", null, 0, null, null));

        assertThat(product.getId()).isNull();
        assertThat(product.getCategory()).isNotNull();
        assertThat(product.getCategory().getId()).isNull();
    }

    @Test
    void flattensCategoryIntoProductResponse() {
        var product = Product.builder().id(7).name("Keyboard").description("Mechanical")
                .availableQuantity(12.5).price(new BigDecimal("99.90"))
                .category(Category.builder().id(3).name("Accessories").description("Desk").build())
                .build();

        assertThat(mapper.toProductResponse(product)).isEqualTo(new ProductResponse(
                7, "Keyboard", "Mechanical", 12.5, new BigDecimal("99.90"), 3, "Accessories", "Desk"));
    }

    @Test
    void purchaseResponseUsesRequestedQuantityAndDoesNotRequireCategory() {
        var product = Product.builder().id(7).name("Keyboard").description("Mechanical")
                .availableQuantity(12.5).price(new BigDecimal("99.90")).build();

        assertThat(mapper.toProductPurchaseResponse(product, 2.5)).isEqualTo(
                new ProductPurchaseResponse(7, "Keyboard", "Mechanical", new BigDecimal("99.90"), 2.5));
        assertThat(product.getAvailableQuantity()).isEqualTo(12.5);
    }

    @Test
    void preservesRejectionOfNullSources() {
        assertThatNullPointerException().isThrownBy(() -> mapper.toProduct(null));
        assertThatNullPointerException().isThrownBy(() -> mapper.toProductResponse(null));
        assertThatNullPointerException().isThrownBy(() -> mapper.toProductPurchaseResponse(null, 1));
    }

    @Test
    void preservesRejectionOfMissingCategoryForProductResponse() {
        assertThatNullPointerException().isThrownBy(() -> mapper.toProductResponse(Product.builder().build()));
    }
}

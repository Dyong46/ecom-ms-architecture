package com.duong.ecommerce.product;

import com.duong.ecommerce.exception.ProductPurchaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;
    @Mock
    private ProductMapper mapper;

    @InjectMocks
    private ProductService service;

    @Test
    void decrementsStockWhenQuantityIsAvailable() {
        var product = Product.builder()
                .id(1)
                .name("Keyboard")
                .availableQuantity(10)
                .price(new BigDecimal("99.90"))
                .build();
        var request = new ProductPurchaseRequest(1, 3);
        var response = new ProductPurchaseResponse(1, "Keyboard", null, new BigDecimal("99.90"), 3);

        when(repository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(product));
        when(mapper.toProductPurchaseResponse(product, 3)).thenReturn(response);

        assertThat(service.purchaseProducts(List.of(request))).containsExactly(response);
        assertThat(product.getAvailableQuantity()).isEqualTo(7);
        verify(repository).save(product);
    }

    @Test
    void rejectsNullPriceBeforeDecrementingAnyStock() {
        var pricedProduct = Product.builder()
                .id(1)
                .availableQuantity(10)
                .price(new BigDecimal("99.90"))
                .build();
        var unpricedProduct = Product.builder()
                .id(2)
                .availableQuantity(5)
                .build();
        when(repository.findAllByIdInOrderById(List.of(1, 2)))
                .thenReturn(List.of(pricedProduct, unpricedProduct));

        assertThatThrownBy(() -> service.purchaseProducts(List.of(
                new ProductPurchaseRequest(1, 3),
                new ProductPurchaseRequest(2, 1)
        )))
                .isInstanceOf(ProductPurchaseException.class)
                .hasMessage("Missing price for product with ID:: 2");

        assertThat(pricedProduct.getAvailableQuantity()).isEqualTo(10);
        assertThat(unpricedProduct.getAvailableQuantity()).isEqualTo(5);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, -1})
    void rejectsInvalidQuantityBeforeAccessingDatabase(double quantity) {
        assertThatThrownBy(() -> service.purchaseProducts(List.of(new ProductPurchaseRequest(1, quantity))))
                .isInstanceOf(ProductPurchaseException.class)
                .hasMessage("Product and a positive quantity are required");

        verifyNoInteractions(repository, mapper);
    }

    @Test
    void rejectsDuplicateProducts() {
        var request = List.of(
                new ProductPurchaseRequest(1, 1),
                new ProductPurchaseRequest(1, 2)
        );

        assertThatThrownBy(() -> service.purchaseProducts(request))
                .isInstanceOf(ProductPurchaseException.class)
                .hasMessage("Duplicate products are not allowed in one purchase request");
    }
}

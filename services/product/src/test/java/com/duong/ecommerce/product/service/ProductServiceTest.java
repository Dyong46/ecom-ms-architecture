package com.duong.ecommerce.product.service;
import com.duong.ecommerce.product.entity.*;
import com.duong.ecommerce.product.payload.request.ProductPurchaseRequest;
import com.duong.ecommerce.product.repository.*;
import com.duong.ecommerce.product.mapper.ProductMapper;
import com.duong.ecommerce.product.exception.ProductPurchaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository repository;
    @Mock ProductVariantRepository variants;
    @Mock OptionValueRepository values;
    @Mock ProductMapper mapper;
    @Mock ApplicationEventPublisher events;
    @InjectMocks ProductService service;
    @Test void validatesAllStockBeforeDecrementingAnySku() {
        var product=Product.builder().id("p").name("Phone").build();
        var first=ProductVariant.builder().id("a").sku("A").product(product).price(BigDecimal.TEN).stockQuantity(10).build();
        var second=ProductVariant.builder().id("b").sku("B").product(product).price(BigDecimal.TEN).stockQuantity(0).build();
        when(variants.findAllForUpdate(List.of("a","b"))).thenReturn(List.of(first,second));
        assertThatThrownBy(()->service.purchaseProducts(List.of(new ProductPurchaseRequest("a",2),new ProductPurchaseRequest("b",1))))
                .isInstanceOf(ProductPurchaseException.class);
        assertThat(first.getStockQuantity()).isEqualTo(10);
    }
    @Test void rejectsDuplicateSkuAndNonpositiveQuantityBeforeDatabaseAccess() {
        assertThatThrownBy(()->service.purchaseProducts(List.of(new ProductPurchaseRequest("a",1),new ProductPurchaseRequest("a",1))))
                .isInstanceOf(ProductPurchaseException.class);
        assertThatThrownBy(()->service.purchaseProducts(List.of(new ProductPurchaseRequest("a",0))))
                .isInstanceOf(ProductPurchaseException.class);
        verifyNoInteractions(variants);
    }
}

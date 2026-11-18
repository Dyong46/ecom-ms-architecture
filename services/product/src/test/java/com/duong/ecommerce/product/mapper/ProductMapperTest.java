package com.duong.ecommerce.product.mapper;
import com.duong.ecommerce.product.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(ProductMapperImpl.class)
class ProductMapperTest {
    @Autowired ProductMapper mapper;
    @Test
    void mapsNestedSkuOptionsWithoutExposingEntityCycles() {
        var option = Option.builder().id("color-id").name("Color").build();
        var value = OptionValue.builder().id("blue-id").value("Blue").option(option).build();
        var product = Product.builder().id("product-id").name("Phone").slug("phone").build();
        var variant = ProductVariant.builder().id("sku-id").sku("PHONE-BLUE").price(BigDecimal.TEN)
                .stockQuantity(5).product(product).build();
        variant.getOptions().add(ProductVariantOptionValue.builder().optionValue(value).productVariant(variant).build());
        product.getVariants().add(variant);
        var result = mapper.toProductResponse(product);
        assertThat(result.id()).isEqualTo("product-id");
        assertThat(result.variants()).hasSize(1);
        assertThat(result.variants().get(0).options().get(0).optionName()).isEqualTo("Color");
        assertThat(result.variants().get(0).options().get(0).value()).isEqualTo("Blue");
        var document = mapper.toSearchDocument(variant);
        assertThat(document.spuId()).isEqualTo("product-id");
        assertThat(document.skuId()).isEqualTo("sku-id");
        assertThat(document.sku()).isEqualTo("PHONE-BLUE");
        assertThat(document.hasStock()).isTrue();
        assertThat(document.attrs().get(0).attrName()).isEqualTo("Color");
        assertThat(document.attrs().get(0).attrValue()).isEqualTo("Blue");
    }
}

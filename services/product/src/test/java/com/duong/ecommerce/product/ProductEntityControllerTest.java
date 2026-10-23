package com.duong.ecommerce.product;

import com.duong.ecommerce.category.Category;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductEntityControllerTest {
    @Test
    void inheritedFindByIdReturnsEntityWithoutCategoryCycle() throws Exception {
        var repository = mock(ProductRepository.class);
        var service = new ProductService(repository, new ProductMapper());
        var product = Product.builder().id(1).name("Keyboard").createdBy("user-id")
                .createdAt(LocalDateTime.of(2026, 10, 23, 12, 0)).build();
        var category = Category.builder().id(2).products(List.of(product)).build();
        product.setCategory(category);
        when(repository.findById(1)).thenReturn(Optional.of(product));
        var mvc = MockMvcBuilders.standaloneSetup(new ProductEntityController(service)).build();
        mvc.perform(get("/api/v1/products/1/entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("user-id"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.category.id").value(2))
                .andExpect(jsonPath("$.category.products").doesNotExist());
        mvc.perform(get("/api/v1/products/99/entity")).andExpect(status().isNotFound());
    }
}

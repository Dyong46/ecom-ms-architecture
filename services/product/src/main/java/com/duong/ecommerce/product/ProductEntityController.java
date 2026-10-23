package com.duong.ecommerce.product;

import com.duong.module.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductEntityController {
    private final ProductService service;

    @GetMapping("/{id}/entity")
    public Product findEntityById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product not found");
    }
}

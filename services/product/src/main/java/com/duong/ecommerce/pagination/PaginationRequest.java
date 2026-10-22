package com.duong.ecommerce.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@Setter
public class PaginationRequest {
    @Min(value = 0, message = "Page must be at least 0")
    private int page = 0;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must not exceed 100")
    private int size = 20;

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by("id").ascending());
    }
}

package com.duong.ecommerce.product.service;

import com.duong.ecommerce.product.entity.*;
import com.duong.ecommerce.product.exception.ProductPurchaseException;
import com.duong.ecommerce.product.mapper.ProductMapper;
import com.duong.ecommerce.product.payload.event.ProductSearchChanged;
import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.payload.response.*;
import com.duong.ecommerce.product.repository.*;
import com.duong.module.common.exception.BadRequestException;
import com.duong.module.common.exception.ConflictException;
import com.duong.module.data.model.specification.SpecificationBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final ProductVariantRepository variants;
    private final OptionValueRepository values;
    private final ProductMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public ProductResponse findById(String id) {
        return mapper.toProductResponse(requireProduct(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toProductResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> filter(Object filter, Pageable pageable) {
        return repository.findAll(SpecificationBuilder.<Product>build(filter), pageable)
                .map(mapper::toProductResponse);
    }

    @Transactional
    public String createProduct(ProductRequest request) {
        if (repository.existsBySlug(request.slug())) throw new ConflictException("Product slug already exists");
        var product = repository.saveAndFlush(Product.builder().name(request.name()).slug(request.slug())
                .description(request.description()).build());
        for (var variant : request.variants()) addVariantTo(product, variant);
        events.publishEvent(new ProductSearchChanged(product.getId()));
        return product.getId();
    }

    @Transactional
    public String addVariant(String productId, VariantRequest request) {
        // Serialize combination checks for this SPU across service instances.
        var product = repository.findForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        var variant = addVariantTo(product, request);
        events.publishEvent(new ProductSearchChanged(productId));
        return variant.getId();
    }

    private ProductVariant addVariantTo(Product product, VariantRequest request) {
        if (request.price() == null || request.price().signum() < 0 || request.stockQuantity() < 0) {
            throw new BadRequestException("SKU price and stock must be nonnegative");
        }
        if (variants.existsBySku(request.sku())) throw new ConflictException("SKU code already exists");
        var selectedIds = new HashSet<>(request.optionValueIds());
        if (selectedIds.size() != request.optionValueIds().size()) {
            throw new BadRequestException("Duplicate option values are not allowed");
        }
        var selected = values.findAllById(selectedIds);
        if (selected.size() != selectedIds.size()) throw new BadRequestException("Option value not found");
        if (selected.stream().map(v -> v.getOption().getId()).distinct().count() != selected.size()) {
            throw new BadRequestException("A SKU can select only one value per option");
        }
        for (var existing : product.getVariants()) {
            var combination = existing.getOptions().stream().map(v -> v.getOptionValue().getId())
                    .collect(Collectors.toSet());
            if (combination.equals(selectedIds)) throw new ConflictException("SKU option combination already exists");
        }
        var variant = variants.saveAndFlush(ProductVariant.builder().product(product).sku(request.sku())
                .price(request.price()).stockQuantity(request.stockQuantity()).build());
        for (var value : selected) {
            variant.getOptions().add(ProductVariantOptionValue.builder()
                    .id(new ProductVariantOptionValueId(variant.getId(), value.getId()))
                    .productVariant(variant).optionValue(value).build());
        }
        product.getVariants().add(variant);
        return variant;
    }

    @Transactional
    public void deleteProduct(String id) {
        var product = repository.findForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        repository.delete(product);
        events.publishEvent(new ProductSearchChanged(id));
    }

    @Transactional
    public void deleteVariant(String productId, String skuId) {
        var product = repository.findForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        var variant = product.getVariants().stream().filter(v -> v.getId().equals(skuId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("SKU not found in Product"));
        product.getVariants().remove(variant);
        events.publishEvent(new ProductSearchChanged(productId));
    }

    private Product requireProduct(String id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Product not found"));
    }

    @Transactional
    public List<ProductPurchaseResponse> purchaseProducts(List<ProductPurchaseRequest> request) {
        if (request == null || request.isEmpty()) throw new ProductPurchaseException("At least one SKU is required");
        if (request.stream().anyMatch(r -> r == null || r.skuId() == null || r.skuId().isBlank() || r.quantity() <= 0)) {
            throw new ProductPurchaseException("SKU and a positive integer quantity are required");
        }
        var ids = request.stream().map(ProductPurchaseRequest::skuId).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw new ProductPurchaseException("Duplicate SKUs are not allowed");
        var stored = variants.findAllForUpdate(ids);
        if (stored.size() != ids.size()) throw new ProductPurchaseException("One or more SKUs do not exist");
        var quantities = request.stream().collect(Collectors.toMap(ProductPurchaseRequest::skuId,
                ProductPurchaseRequest::quantity));
        // Validate the entire batch before changing any stock.
        for (var variant : stored) {
            if (variant.getPrice() == null || variant.getPrice().signum() < 0) {
                throw new ProductPurchaseException("Invalid price for SKU: " + variant.getId());
            }
            if (variant.getStockQuantity() < quantities.get(variant.getId())) {
                throw new ProductPurchaseException("Insufficient stock quantity for SKU: " + variant.getId());
            }
        }
        var responses = new ArrayList<ProductPurchaseResponse>();
        for (var variant : stored) {
            int quantity = quantities.get(variant.getId());
            variant.setStockQuantity(variant.getStockQuantity() - quantity);
            var options = new TreeMap<String, String>();
            for (var selected : variant.getOptions()) {
                options.put(selected.getOptionValue().getOption().getName(), selected.getOptionValue().getValue());
            }
            var product = variant.getProduct();
            responses.add(new ProductPurchaseResponse(product.getId(), variant.getId(), variant.getSku(),
                    product.getName(), product.getDescription(), Map.copyOf(options), variant.getPrice(), quantity));
        }
        stored.stream().map(v -> v.getProduct().getId()).distinct()
                .forEach(id -> events.publishEvent(new ProductSearchChanged(id)));
        return responses;
    }
}

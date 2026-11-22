package com.duong.ecommerce.product.service;

import com.duong.ecommerce.product.entity.*;
import com.duong.ecommerce.product.payload.event.ProductSearchChanged;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import com.duong.ecommerce.product.mapper.ProductMapper;
import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.payload.response.*;
import com.duong.ecommerce.product.repository.*;
import com.duong.module.common.exception.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProductOptionService {
    private final OptionRepository options;
    private final OptionValueRepository values;
    private final ProductMapper mapper;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Transactional
    public String create(OptionRequest request) {
        if (options.existsByName(request.name())) throw new ConflictException("Option name already exists");
        return options.save(Option.builder().name(request.name()).build()).getId();
    }

    @Transactional
    public String addValue(String optionId, OptionValueRequest request) {
        var option = options.findById(optionId).orElseThrow(() -> new EntityNotFoundException("Option not found"));
        if (values.existsByOptionIdAndValue(optionId, request.value())) {
            throw new ConflictException("Option value already exists");
        }
        return values.save(OptionValue.builder().option(option).value(request.value())
                .synonyms(request.synonyms()).build()).getId();
    }

    @Transactional(readOnly = true)
    public Page<OptionResponse> findAll(Pageable pageable) {
        return options.findAll(pageable).map(mapper::toOptionResponse);
    }

    @Transactional
    public void delete(String optionId) {
        var option = options.findById(optionId).orElseThrow(() -> new EntityNotFoundException("Option not found"));
        var affected = affectedProducts("ov.option_id", optionId);
        // Use the selected database cascade to remove values and their join rows.
        entityManager.createNativeQuery("delete from product_option where id = :id")
                .setParameter("id", option.getId()).executeUpdate();
        entityManager.clear();
        affected.forEach(id -> events.publishEvent(new ProductSearchChanged(id)));
    }

    @Transactional
    public void deleteValue(String optionId, String valueId) {
        var value = values.findById(valueId).orElseThrow(() -> new EntityNotFoundException("Option value not found"));
        if (!value.getOption().getId().equals(optionId)) throw new EntityNotFoundException("Value not found in Option");
        var affected = affectedProducts("ov.id", valueId);
        entityManager.createNativeQuery("delete from option_value where id = :id")
                .setParameter("id", valueId).executeUpdate();
        entityManager.clear();
        affected.forEach(id -> events.publishEvent(new ProductSearchChanged(id)));
    }

    private List<String> affectedProducts(String field, String id) {
        // field is an internal constant selected by the two callers, never request input.
        List<?> rows = entityManager.createNativeQuery("select distinct v.product_id from product_variant v "
                + "join product_variant_option_value s on s.product_variant_id = v.id "
                + "join option_value ov on ov.id = s.option_value_id where " + field + " = :id")
                .setParameter("id", id).getResultList();
        return rows.stream().map(String.class::cast).toList();
    }
}

package com.duong.module.data.model.service;

import static com.duong.module.common.constant.ErrorCode.ENTITY_NOT_FOUND;
import static com.duong.module.common.constant.ErrorCode.ENTITY_WAS_DELETED;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.duong.module.data.model.entity.BaseEntity;
import com.duong.module.data.model.specification.SpecificationBuilder;
import com.duong.module.common.exception.BadRequestException;
import com.duong.module.common.exception.InternalServerErrorException;
import com.duong.module.common.exception.NotFoundException;
import com.duong.module.common.util.GenericUtils;
import com.duong.module.web.payload.filter.BaseEntityFilter;

public interface BaseService<E extends BaseEntity<ID>, ID> {

  JpaRepository<E, ID> getRepository();

  default Page<E> findAll(org.springframework.data.domain.Pageable pageable) {
    return getRepository().findAll(pageable);
  }

  default <D> Page<D> findAllAndMap(org.springframework.data.domain.Pageable pageable,
      Function<? super E, D> mapper) {
    return findAll(pageable).map(mapper);
  }

  @org.springframework.transaction.annotation.Transactional
  default void softDelete(ID id) {
    E entity = findById(id);
    entity.markDeleted();
    getRepository().save(entity);
  }

  default Class<E> getEntityClass() {
    return GenericUtils.getTypeParameter(getClass());
  }

  default <D> List<D> findAllAndMap(Function<? super E, ? extends D> mapper) {
    return getRepository().findAll()
        .stream()
        .map(mapper)
        .collect(Collectors.toList());
  }

  default List<E> findAllById(Collection<ID> ids) {
    return CollectionUtils.isNotEmpty(ids) ? getRepository().findAllById(ids)
        : Collections.emptyList();
  }

  default Map<ID, E> findAllByIdToMap(Collection<ID> ids, Function<E, ID> idFunction) {
    return findAllById(ids)
        .stream()
        .collect(Collectors.toMap(idFunction, x -> x));
  }

  default E findById(ID id, Boolean deleted) {
    return getRepository().findById(id)
        .filter(entity -> deleted == null || deleted.equals(entity.isDeleted()))
        .orElseThrow(() -> new NotFoundException("Not found " + getEntityClass().getSimpleName()
            + " with id: " + id)
            .errorCode(ENTITY_NOT_FOUND)
            .extraData(
                "entity", getEntityClass().getSimpleName(),
                "id", id
            ));
  }

  default E findById(ID id) {
    E entity = findById(id, null);
    if (entity.isDeleted()) {
      throw new BadRequestException("Entity " + getEntityClass().getSimpleName() +
          " with id " + id + " was deleted")
          .errorCode(ENTITY_WAS_DELETED)
          .extraData(
              "entity", getEntityClass().getSimpleName(),
              "id", id
          );
    }
    return entity;
  }

  default E findByNullableId(ID id, Boolean deleted) {
    return Optional.ofNullable(id)
        .map(i -> findById(i, deleted))
        .orElse(null);
  }

  default E findByNullableId(ID id) {
    return Optional.ofNullable(id)
        .map(this::findById)
        .orElse(null);
  }

  default E findByIdOrElse(ID id, E entity) {
    return getRepository().findById(id)
        .orElse(entity);
  }

  default E findByIdOrNull(ID id) {
    return findByIdOrElse(id, null);
  }

  default E findByIdOrGet(ID id, Supplier<? extends E> supplier) {
    return getRepository().findById(id)
        .orElseGet(supplier);
  }

  @SuppressWarnings("unchecked")
  default <F> Page<E> filter(BaseEntityFilter<F> filter) {

    JpaRepository<E, ID> repository = getRepository();
    if (repository instanceof JpaSpecificationExecutor) {
      JpaSpecificationExecutor<E> executor = (JpaSpecificationExecutor<E>) repository;
      Specification<E> specification = SpecificationBuilder.build(filter.getFilters().toArray());
      return executor.findAll(specification, filter.getPageable());
    } else {
      throw new InternalServerErrorException(
          "Repository of entity " + getEntityClass().getSimpleName() +
              " not support specification filter");
    }

  }

}

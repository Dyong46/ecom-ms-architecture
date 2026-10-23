package com.duong.module.data.model.specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import jakarta.persistence.metamodel.ManagedType;
import org.springframework.data.jpa.domain.Specification;
import com.duong.module.common.util.ReflectionUtils;
import com.duong.module.common.util.StrUtils;

public class SpecificationBuilder {

	public static <T> Specification<T> build(Object... filters) {
		return (root, query, builder) -> {
			if (!isCountQuery(query)) {
				Arrays.stream(root.getJavaType().getDeclaredFields())
						.filter(field -> field.isAnnotationPresent(ManyToOne.class))
						.filter(field -> field.getAnnotation(ManyToOne.class).fetch().equals(FetchType.EAGER))
						.forEach(field -> root.fetch(field.getName(), JoinType.LEFT));
			}
			return builder.and(deletedAtNullPredicate(root, builder), toPredicate(root, query, builder, filters));
		};
	}

	private static Predicate toPredicate(From<?, ?> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			Object... filters) {
		if (filters.length == 0) {
			return builder.conjunction();
		} else {
			return builder.or(Arrays.stream(filters).map(filter -> {
				List<Predicate> predicates = ReflectionUtils.getAllFields(filter.getClass()).stream()
						.map(field -> toPredicate(root, query, builder, field.getName(),
								ReflectionUtils.getValue(filter, field)))
						.collect(Collectors.toCollection(ArrayList::new));
				predicates.add(deletedAtNullPredicate(root, builder));
				return builder.and(predicates.toArray(new Predicate[0]));
			}).toArray(Predicate[]::new));
		}
	}

	private static Predicate toPredicate(From<?, ?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String field,
			Object value) {
		value = cleanValue(value);
		if (value == null) {
			return builder.and();
		}
		PredicateTypeEnum predicateType = PredicateTypeEnum.parse(field);
		field = cleanField(field, predicateType);
		try {
			if (root.getModel() instanceof ManagedType<?> managedType
					&& !managedType.getAttribute(field).isAssociation()) {
				return toPredicate(root, builder, field, value, predicateType);
			}
			root = root.join(field, JoinType.LEFT);
			return builder.and(toPredicate(root, query, builder, value));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid filter field: " + field, e);
		}
	}

	private static Predicate toPredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value,
			PredicateTypeEnum predicateType) {
		switch (predicateType) {
		case EQ:
			return toEqPredicate(root, builder, field, value);
		case NE:
			return toNePredicate(root, builder, field, value);
		case ISNULL:
			return toNullPredicate(root, builder, field, (Boolean) value);
		case IN:
			if (value instanceof Collection) {
				Collection<?> values = (Collection<?>) value;
				if (CollectionUtils.isEmpty(values)) {
					return builder.disjunction();
				} else if (values.stream().anyMatch(Objects::isNull)) {
					List<?> nonNullValues = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
					return builder.or(toNullPredicate(root, builder, field, true),
							toInPredicate(root, builder, field, nonNullValues));
				} else {
					return toInPredicate(root, builder, field, values);
				}
			} else {
				return toInPredicate(root, builder, field, Collections.singleton(value));
			}
		case NOTIN:
			Collection<?> excluded = value instanceof Collection<?> collection ? collection : Collections.singleton(value);
			if (excluded.isEmpty()) return builder.conjunction();
			List<?> nonNull = excluded.stream().filter(Objects::nonNull).toList();
			Predicate notIn = nonNull.isEmpty() ? builder.conjunction() : root.get(field).in(nonNull).not();
			return excluded.stream().anyMatch(Objects::isNull) ? builder.and(root.get(field).isNotNull(), notIn) : notIn;
		case LIKE:
			return toLikePredicate(root, builder, field, value);
		case GT:
			return toGtPredicate(root, builder, field, value);
		case GTE:
			return toGtePredicate(root, builder, field, value);
		case LT:
			return toLtPredicate(root, builder, field, value);
		case LTE:
			return toLtePredicate(root, builder, field, value);
		}
		return builder.and();
	}

	private static Predicate toEqPredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		if (value instanceof String) {
			value = StrUtils.normalize((String) value).toLowerCase();
			return builder.equal(builder.lower(root.get(field)), value);
		} else {
			return builder.equal(root.get(field), value);
		}
	}

	private static Predicate toNePredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		return builder.notEqual(root.get(field), value);
	}

	private static Predicate toNullPredicate(From<?, ?> root, CriteriaBuilder builder, String field, boolean isNull) {
		if (isNull) {
			return builder.and(root.get(field).isNull());
		} else {
			return builder.and(root.get(field).isNotNull());
		}
	}

	private static Predicate toInPredicate(From<?, ?> root, CriteriaBuilder builder, String field,
			Collection<?> values) {
		return builder.and(root.get(field).in(values));
	}

	private static Predicate toLikePredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		String pattern = value.toString().toLowerCase(java.util.Locale.ROOT)
				.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return builder.like(builder.lower(root.get(field)), "%" + pattern + "%", '\\');
	}

	@SuppressWarnings("unchecked")
	private static Predicate toGtPredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		if (value instanceof Number) {
			return builder.gt(root.get(field), (Number) value);
		}
		if (value instanceof Comparable) {
			return builder.greaterThan(root.get(field), (Comparable) value);
		}
		return builder.greaterThan(root.get(field), value.toString());
	}

	@SuppressWarnings("unchecked")
	private static Predicate toGtePredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		if (value instanceof Number) {
			return builder.ge(root.get(field), (Number) value);
		}
		if (value instanceof Comparable) {
			return builder.greaterThanOrEqualTo(root.get(field), (Comparable) value);
		}
		return builder.greaterThanOrEqualTo(root.get(field), value.toString());
	}

	@SuppressWarnings("unchecked")
	private static Predicate toLtPredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		if (value instanceof Number) {
			return builder.lt(root.get(field), (Number) value);
		}
		if (value instanceof Comparable) {
			return builder.lessThan(root.get(field), (Comparable) value);
		}
		return builder.lessThan(root.get(field), value.toString());
	}

	@SuppressWarnings("unchecked")
	private static Predicate toLtePredicate(From<?, ?> root, CriteriaBuilder builder, String field, Object value) {
		if (value instanceof Number) {
			return builder.le(root.get(field), (Number) value);
		}
		if (value instanceof Comparable) {
			return builder.lessThanOrEqualTo(root.get(field), (Comparable) value);
		}
		return builder.lessThanOrEqualTo(root.get(field), value.toString());
	}

	private static Predicate deletedAtNullPredicate(From<?, ?> root, CriteriaBuilder builder) {
		try {
			return builder.isNull(root.get("deletedAt"));
		} catch (Exception e) {
			return builder.and();
		}
	}

	private static String cleanField(String field, PredicateTypeEnum predicateType) {
		return field.replaceAll(predicateType.alias() + "$", "");
	}

	private static Object cleanValue(Object value) {
		if (value instanceof String) {
			return StringUtils.trimToNull((String) value);
		} else {
			return value;
		}
	}

	private static boolean isCountQuery(CriteriaQuery<?> query) {
		return Long.class.isAssignableFrom(query.getResultType());
	}

}

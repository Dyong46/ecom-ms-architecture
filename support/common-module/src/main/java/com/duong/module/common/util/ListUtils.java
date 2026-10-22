package com.duong.module.common.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ListUtils {

    private static ModelMapper modelMapper;

	private static final ObjectMapper objectMapper = new ObjectMapper();

    public ListUtils(ModelMapper mapper) {
        ListUtils.modelMapper = mapper;
    }

    public static <F, T> List<T> transform(List<F> fromList, Function<? super F, ? extends T> function) {
        return fromList.stream()
                .map(function)
                .collect(Collectors.toList());
    }
    
    public static <F, T> Set<T> transform(Set<F> fromList, Function<? super F, ? extends T> function) {
        return fromList.stream()
                .map(function)
                .collect(Collectors.toSet());
    }

    public static <F, T> List<T> mapAll(List<F> fromList, Class<T> clazz) {
        return transform(fromList, item -> modelMapper.map(item, clazz));
    }
	
	public static <F, T> Set<T> mapAll(Set<F> fromList, Class<T> clazz) {
		return transform(fromList, item -> modelMapper.map(item, clazz));
	}

    public static <T> List<T> filter(Collection<T> list, Predicate<T> predicate) {
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    public static <T> T findFirst(Collection<T> list, Predicate<T> predicate) {
        return list.stream().filter(predicate).findFirst().orElse(null);
    }

    public static <T> void addIfNotNull(List<T> list, T item) {
        if (Objects.nonNull(item)) {
            list.add(item);
        }
    }

    public static <T> String[] toStringArray(List<T> list) {
        try {
            String[] strings = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                strings[i] = String.valueOf(list.get(i));
            }
            return strings;
        } catch (Exception e) {
            log.error("Convert to array string got error: {}", e.getLocalizedMessage());
            return new String[]{};
        }
    }

    public static <T> String[] toStringArray(Collection<T> list) {
        try {
            String[] strings = new String[list.size()];
            int i = 0;
            
            for (T item : list) {
                strings[i++] = String.valueOf(item);
            }
            
            return strings;
        } catch (Exception e) {
            log.error("Convert to array string got error: {}", e.getLocalizedMessage());
            return new String[]{};
        }
    }
	
	public static <T> T get(List<T> list, int index) {
		if (CollectionUtils.isEmpty(list) || list.size() <= index) {
			return null;
		}
		return list.get(index);
	}

    public static <T> T getFirstElement(final Iterable<T> elements) {
        return elements.iterator().next();
    }

    public static <T> T getLastElement(final Iterable<T> elements) {
        T lastElement = null;

        for (T element : elements) {
            lastElement = element;
        }

        return lastElement;
    }
	
	public static <T> T random(List<T> list) {
		Random random = new Random();
		return list.get(random.nextInt(list.size()));
	}
	
	public static <T> List<T> convert(String arrString, Class<T> clazz) throws Exception {
		JavaType type = objectMapper.getTypeFactory().
				constructCollectionType(List.class, clazz);
		return objectMapper.readValue(arrString, type);
	}
	
	public static <K,V> Map<K, V> map(List<V> list, Function<V, K> keyFunc) {
		return list.stream().collect(Collectors.toMap(keyFunc, t -> t));
	}

}

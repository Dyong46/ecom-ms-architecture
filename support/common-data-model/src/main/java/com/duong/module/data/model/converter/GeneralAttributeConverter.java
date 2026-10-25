package com.duong.module.data.model.converter;

import java.util.Arrays;
import java.util.Objects;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.duong.module.common.util.GenericUtils;

@Converter
public interface GeneralAttributeConverter<X extends Enum<X>, Y> extends AttributeConverter<X, Y> {

  @Override
  default Y convertToDatabaseColumn(X attribute) {
    return attribute != null ? map(attribute) : null;
  }

  @Override
  default X convertToEntityAttribute(Y dbData) {
    if (dbData != null) {
      return Arrays.stream(GenericUtils.<X>getTypeParameter(getClass()).getEnumConstants())
          .filter(x -> Objects.equals(map(x), dbData))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
              "No enum mapping found for database value: " + dbData));
    } else {
      return null;
    }
  }

  Y map(X x);

}

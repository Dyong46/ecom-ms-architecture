package com.duong.module.data.model.converter;

public interface NamingAttributeConverter<X extends Enum<X>> extends
    GeneralAttributeConverter<X, String> {

  @Override
  default String map(X x) {
    return x.name();
  }

}

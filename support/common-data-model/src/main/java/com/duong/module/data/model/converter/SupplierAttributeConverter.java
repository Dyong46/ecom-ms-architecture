package com.duong.module.data.model.converter;

import java.util.function.Supplier;

public interface SupplierAttributeConverter<X extends Enum<X> & Supplier<Y>, Y> extends
    GeneralAttributeConverter<X, Y> {

  @Override
  default Y map(X x) {
    return x.get();
  }

}

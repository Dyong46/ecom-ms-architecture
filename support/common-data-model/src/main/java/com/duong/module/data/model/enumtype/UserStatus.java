package com.duong.module.data.model.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {

  ACTIVE("Active"),
  INACTIVE("Inactive"),
  ;

  private final String name;

}

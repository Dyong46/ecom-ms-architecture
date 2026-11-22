package com.duong.module.data.model.entity.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatusEnum {

  ACTIVE("Active"),
  INACTIVE("Inactive"),
  ;

  private final String name;

}

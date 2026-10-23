package com.duong.module.data.model.specification;

public enum PredicateTypeEnum {

  EQ("Eq"),
  NE("Ne"),
  ISNULL("Isnull"),
  IN("In"),
  NOTIN("Notin"),
  LIKE("Lk"),
  GT("Gt"),
  GTE("Gte"),
  LT("Lt"),
  LTE("Lte");

  private final String alias;

  PredicateTypeEnum(String alias) {
    this.alias = alias;
  }

  public static PredicateTypeEnum parse(String key) {
    for (PredicateTypeEnum predicateType : values()) {
      if (key.endsWith(predicateType.alias)) {
        return predicateType;
      }
    }
    return EQ;
  }

  public String alias() {
    return alias;
  }

}

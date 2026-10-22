package com.duong.module.common.util;

import java.util.List;

public class PasswordUtils {

  public static boolean containsSubstring(String s1, String s2, int length) {
    if (s1 == null || s2 == null) {
      return false;
    }
    for (int i = 0; i <= s1.length() - length; i++) {
      String subStr = s1.substring(i, i + length);
      if (s2.contains(subStr)) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsRestrictedChar(String password, List<String> restrictedStrings) {
    return restrictedStrings.stream()
            .anyMatch(password::contains);
  }

}

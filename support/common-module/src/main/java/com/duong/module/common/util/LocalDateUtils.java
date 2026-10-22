package com.duong.module.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class LocalDateUtils {

  public static String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss";

  public static LocalDateTime toLocalDateTime(long timestamp) {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

  public static Long toLongValue(LocalDateTime time) {
    if (Objects.isNull(time)) {
      return null;
    }
    ZonedDateTime zdt = ZonedDateTime.of(time, ZoneId.systemDefault());
    return zdt.toInstant().toEpochMilli();
  }

  public static LocalDateTime stringToLocalDateTime(String input, String pattern) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    return LocalDateTime.parse(input, formatter);
  }

  public static LocalDateTime stringToLocalDateTime(String input) {
    if (StringUtils.isBlank(input)) {
      return null;
    }
    return stringToLocalDateTime(input, ISO_8601);
  }

  public static Date toDate(LocalDateTime input) {
    return Date.from(input.atZone(ZoneId.systemDefault()).toInstant());
  }

  public static String formatDateTimeRange(LocalDateTime start, LocalDateTime end, Integer offset) {
    start = start.plusHours(offset);
    end = end.plusHours(offset);
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm");
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    if (start.toLocalDate().equals(end.toLocalDate())) {
      return String.format("%s - %s %s",
          start.format(timeFormatter),
          end.format(timeFormatter),
          start.format(dateFormatter));
    } else {
      return String.format("%s %s - %s %s",
          start.format(timeFormatter),
          start.format(dateFormatter),
          end.format(timeFormatter),
          end.format(dateFormatter));
    }
  }

  public static LocalDateTime roundToNearestHour(LocalDateTime dateTime) {
    LocalDateTime truncated = dateTime.truncatedTo(ChronoUnit.HOURS);
    if (dateTime.getMinute() >= 30) {
      truncated = truncated.plusHours(1);
    }
    return truncated;
  }

}

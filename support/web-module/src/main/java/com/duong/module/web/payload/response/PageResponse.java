package com.duong.module.web.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.Page;
import com.duong.module.common.util.LogUtils;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageResponse<T> {

  @JsonProperty("content")
  private List<T> content;

  @JsonProperty("page_info")
  private PageInfo pageInfo;

  @JsonProperty("request_id")
  private String requestId;

  public <U> PageResponse<U> map(Function<? super T, ? extends U> converter) {
    return PageResponse.<U>builder()
        .content(getContent().stream()
            .map(converter)
            .collect(Collectors.toList()))
        .pageInfo(getPageInfo())
        .requestId(LogUtils.getTraceId())
        .build();
  }

  public static <T> PageResponse<T> build(Page<T> page) {
    return PageResponse.<T>builder()
        .content(page.getContent())
        .pageInfo(PageInfo.build(page))
        .requestId(LogUtils.getTraceId())
        .build();
  }

  public static <R, T> PageResponse<T> build(Page<R> page, Function<R, T> converter) {
    return build(page).map(converter);
  }

  @Data
  @SuperBuilder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PageInfo {

    @JsonProperty("number")
    private int number;

    @JsonProperty("size")
    private int size;

    @JsonProperty("number_of_elements")
    private int numberOfElements;

    @JsonProperty("is_first")
    private boolean first;

    @JsonProperty("is_last")
    private boolean last;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("total_elements")
    private long totalElements;

    public static PageInfo build(Page<?> page) {
      return PageInfo.builder()
          .number(page.getNumber() + 1)
          .size(page.getSize())
          .numberOfElements(page.getNumberOfElements())
          .first(page.isFirst())
          .last(page.isLast())
          .totalPages(page.getTotalPages())
          .totalElements(page.getTotalElements())
          .build();
    }

  }

}

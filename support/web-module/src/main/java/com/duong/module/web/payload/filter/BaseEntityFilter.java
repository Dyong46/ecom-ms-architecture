package com.duong.module.web.payload.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collection;
import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.Pageable;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseEntityFilter<F> {

  private Collection<F> filters;

  private Pageable pageable;

  public static <F> BaseEntityFilter<F> of(F filter, Pageable pageable) {
    return of(Collections.singleton(filter), pageable);
  }

}

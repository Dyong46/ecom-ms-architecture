package com.duong.module.data.model.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import com.duong.module.common.util.LocalDateUtils;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public class BaseRevisionInfo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @RevisionNumber
  @Column(name = "REV")
  private Integer rev;

  @RevisionTimestamp
  @Column(name = "REVTSTMP")
  private Long timestamp;

  @Column(name = "request_time")
  private LocalDateTime requestTime;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "user_id")
  private Integer userId;

  @Column(name = "request_method")
  private String requestMethod;

  @Column(name = "request_uri")
  private String requestUri;

  @PrePersist
  public void prePersist() {
    requestTime = LocalDateUtils.toLocalDateTime(timestamp);
  }

}

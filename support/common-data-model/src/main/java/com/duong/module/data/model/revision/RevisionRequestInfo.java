package com.duong.module.data.model.revision;

public interface RevisionRequestInfo {

  String getRequestId();

  Integer getUserId();

  String getRequestMethod();

  String getRequestUri();

}

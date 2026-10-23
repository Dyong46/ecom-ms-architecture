package com.duong.module.data.model.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.RevisionListener;
import com.duong.module.data.model.entity.BaseRevisionInfo;
import com.duong.module.data.model.revision.RevisionRequestInfo;
import com.duong.module.web.dto.DebuggingDTO;

@Slf4j
@RequiredArgsConstructor
public class AbstractRevisionListener implements RevisionListener {

	private final RevisionRequestInfo revisionRequestInfo;

	@Override
	public void newRevision(Object revisionEntity) {
		try {
			if (revisionEntity instanceof BaseRevisionInfo && revisionRequestInfo != null) {
				BaseRevisionInfo revisionInfo = (BaseRevisionInfo) revisionEntity;
				revisionInfo.setRequestId(revisionRequestInfo.getRequestId());
				revisionInfo.setUserId(revisionRequestInfo.getUserId());
				revisionInfo.setRequestMethod(revisionRequestInfo.getRequestMethod());
				revisionInfo.setRequestUri(revisionRequestInfo.getRequestUri());
			}
		} catch (Exception e) {
			log.error("Error when update revision: {}", DebuggingDTO.build(e));
		}
	}

}

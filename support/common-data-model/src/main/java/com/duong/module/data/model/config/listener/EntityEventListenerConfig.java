package com.duong.module.data.model.config.listener;

import java.util.List;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import com.duong.module.data.model.listener.EntityEventListenerWrapper;

@Configuration
public class EntityEventListenerConfig<T> {

	private final EntityManagerFactory entityManagerFactory;

	private final List<EntityEventListenerWrapper<T>> entityEventListenerWrappers;

	public EntityEventListenerConfig(@Autowired(required = false) EntityManagerFactory entityManagerFactory,
			List<EntityEventListenerWrapper<T>> entityEventListenerWrappers) {
		this.entityManagerFactory = entityManagerFactory;
		this.entityEventListenerWrappers = entityEventListenerWrappers;
	}

	@PostConstruct
	private void init() {
		if (entityManagerFactory != null && !CollectionUtils.isEmpty(entityEventListenerWrappers)) {
			SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
			EventListenerRegistry registry = sessionFactory.getServiceRegistry()
					.getService(EventListenerRegistry.class);
			entityEventListenerWrappers.forEach(wrapper -> registry.getEventListenerGroup(wrapper.getEventType())
					.appendListener(wrapper.getListener()));
		}
	}

}

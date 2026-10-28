package com.duong.module.data.model.listener;

import org.hibernate.event.spi.EventType;

public interface EntityEventListenerWrapper<T> {

	T getListener();

	@SuppressWarnings("unchecked")
	default EventType<T> getEventType() {
		return (EventType<T>) EventType.values().stream()
				.filter(eventType -> eventType.baseListenerInterface().isAssignableFrom(getListener().getClass()))
				.findFirst().orElse(null);
	}

}

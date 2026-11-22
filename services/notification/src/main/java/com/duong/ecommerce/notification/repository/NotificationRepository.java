package com.duong.ecommerce.notification.repository;

import com.duong.ecommerce.notification.entity.Notification;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
}

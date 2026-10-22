package com.duong.module.common.constant;

public interface KafkaConsumerGroup {

	String TICKET_CONSUMER = "ticketConsumerGroup";
	String ORDER_CONSUMER = "orderPurchaseConsumerGroup";
	String BATCH_CONSUMER = "batchConsumerGroup";
	
	String ORDER_CALLBACK_CONSUMER = "orderCallbackConsumerGroup";
	
	String LOG_CONSUMER = "logConsumerGroup";
	String NOTIFY = "groupNotify";
	
	String TEST = "kafkaGroupTest";

}

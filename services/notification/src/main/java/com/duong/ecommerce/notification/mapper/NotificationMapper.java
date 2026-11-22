package com.duong.ecommerce.notification.mapper;

import com.duong.ecommerce.notification.entity.Notification;
import com.duong.module.mail.message.MailMessage;
import java.util.List;
import java.util.Objects;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotificationMapper {
    // Update only the rendered email snapshot; delivery state belongs to the service.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "senderAddress", source = "senderAddress")
    @Mapping(target = "recipient", source = "receivers", qualifiedByName = "firstRecipient")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "html", source = "html")
    void updateMailSnapshot(MailMessage message, @MappingTarget Notification notification);

    @Named("firstRecipient")
    default String firstRecipient(List<String> receivers) {
        return receivers.get(0);
    }

    @BeforeMapping
    default void requireMessage(MailMessage message) {
        Objects.requireNonNull(message, "message");
    }
}

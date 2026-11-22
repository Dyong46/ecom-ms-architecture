package com.duong.ecommerce.customer.entity;

import com.duong.module.mongo.model.entity.BaseSoftDeletableMongoDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Getter
@Setter
@Document
public class Customer extends BaseSoftDeletableMongoDocument {

    @Id
    private String id;

    private String firstname;

    private String lastname;

    private String email;

    private Address address;
}

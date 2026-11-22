package com.duong.ecommerce.product.integration;

import com.duong.ecommerce.product.integration.search.SkuSearchDocument;
import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.repository.ProductRepository;
import com.duong.ecommerce.product.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:productsearch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=validate",
        "spring.elasticsearch.connection-timeout=1s","spring.elasticsearch.socket-timeout=2s",
        "spring.data.elasticsearch.repositories.enabled=false"})
@AutoConfigureMockMvc @Testcontainers
class ProductSearchIntegrationTest {
    @Container static ElasticsearchContainer elasticsearch=new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
            .withEnv("xpack.security.enabled","false").withEnv("ES_JAVA_OPTS","-Xms512m -Xmx512m");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",elasticsearch::getHttpHostAddress);
    }
    @Autowired ProductService products;
    @Autowired ProductOptionService options;
    @Autowired ProductSearchService search;
    @Autowired ProductRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @SpyBean ElasticsearchOperations operations;
    @SpyBean com.duong.ecommerce.product.mapper.ProductMapper mapper;

    @BeforeEach void clean() {
        jdbc.update("delete from product");
        jdbc.update("delete from product_option");
        search.reindex();
    }
    private VariantRequest sku(String code,int stock,String... values) {
        return new VariantRequest(code,BigDecimal.TEN,stock,List.of(values));
    }
    private String create(String name,String description,VariantRequest... variants) {
        return products.createProduct(new ProductRequest(name,UUID.randomUUID().toString(),description,List.of(variants)));
    }
    private String create(String name,String description) {
        return create(name,description,sku("TEST-"+UUID.randomUUID(),5));
    }
    private String value(String option,String value) {
        return options.addValue(option,new OptionValueRequest(value,null));
    }
    private ProductSearchRequest request(String keyword,String... pairs) {
        var request=new ProductSearchRequest();request.setQ(keyword);
        for(int i=0;i<pairs.length;i+=2) {
            var option=new ProductSearchRequest.OptionFilter();option.setName(pairs[i]);option.setValue(pairs[i+1]);
            request.getOptions().add(option);
        }
        return request;
    }
    private void refresh() { operations.indexOps(SkuSearchDocument.class).refresh(); }
    private long documentCount() { return operations.count(Query.findAll(),SkuSearchDocument.class); }

    @Test void indexesSkuOnlyAfterCommitAndSearchesNameAndDescriptionThroughPublicApi() throws Exception {
        String id=new TransactionTemplate(transactions).execute(status->{
            var created=create("Mechanical Keyboard","Quiet typing accessory");
            refresh();assertThat(search.search(request("keyboard"),PageRequest.of(0,20))).isEmpty();return created;
        });
        refresh();
        mvc.perform(get("/api/v1/products/search").param("q","KEYBOARD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].spuId").value(id))
                .andExpect(jsonPath("$.data.content[0].skuId").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].skuPrice").value(10))
                .andExpect(jsonPath("$.data.page_info.total_elements").value(1));
        assertThat(search.search(request("accessory"),PageRequest.of(0,20)))
                .extracting(SkuSearchDocument::spuId).containsExactly(id);
        assertThat(search.search(request("unmatched"),PageRequest.of(0,20))).isEmpty();
    }

    @Test void filtersAttributesOnSameSkuWithoutCrossVariantOrCrossAttributeMatches() {
        var color=options.create(new OptionRequest("Color"));var black=value(color,"Black");var white=value(color,"White");
        var storage=options.create(new OptionRequest("Storage"));var small=value(storage,"128GB");var large=value(storage,"256GB");
        var id=create("Phone","Mobile",sku("PHONE-BLACK-128",3,black,small),sku("PHONE-WHITE-256",5,white,large));
        refresh();
        assertThat(search.search(request("phone","Color","Black","Storage","256GB"),PageRequest.of(0,20))).isEmpty();
        assertThat(search.search(request("phone","Color","256GB"),PageRequest.of(0,20))).isEmpty();
        var result=search.search(request("phone","Color","Black","Storage","128GB"),PageRequest.of(0,20));
        assertThat(result).extracting(SkuSearchDocument::sku).containsExactly("PHONE-BLACK-128");
        assertThat(result.getContent().get(0).spuId()).isEqualTo(id);
        assertThat(result.getContent().get(0).attrs()).hasSize(2);
    }

    @Test void collapseCountsSpusAndExactSkuCodeSelectsTheMatchingVariant() throws Exception {
        var color=options.create(new OptionRequest("Color"));var black=value(color,"Black");var white=value(color,"White");
        create("Phone One","Mobile",sku("ONE-BLACK",3,black),sku("ONE-WHITE",4,white));
        create("Phone Two","Mobile",sku("TWO-BLACK",5,black),sku("TWO-WHITE",6,white));
        refresh();
        var first=search.search(request("phone"),PageRequest.of(0,1));
        var second=search.search(request("phone"),PageRequest.of(1,1));
        assertThat(documentCount()).isEqualTo(4);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent().get(0).spuId()).isNotEqualTo(second.getContent().get(0).spuId());
        assertThat(search.search(request("two-white"),PageRequest.of(0,20)))
                .extracting(SkuSearchDocument::sku).containsExactly("TWO-WHITE");
        assertThat(search.search(request(null),PageRequest.of(0,20)).getTotalElements()).isEqualTo(2);
        mvc.perform(get("/api/v1/products/search").param("q","phone").param("page","2").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page_info.total_elements").value(2));
        mvc.perform(get("/api/v1/products/search").param("page","501")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products/search/reindex")).andExpect(status().is4xxClientError());
    }

    @Test void stockChangesAfterPurchaseUpdateTheSameSkuAndStockFilter() {
        var color=options.create(new OptionRequest("Color"));var black=value(color,"Black");var white=value(color,"White");
        var id=create("Phone","Mobile",sku("BLACK",1,black),sku("WHITE",4,white));
        var blackId=products.findById(id).variants().stream().filter(v->v.sku().equals("BLACK")).findFirst().orElseThrow().id();
        products.purchaseProducts(List.of(new ProductPurchaseRequest(blackId,1)));refresh();
        var inStock=request("phone");inStock.setHasStock(true);
        assertThat(search.search(inStock,PageRequest.of(0,20))).extracting(SkuSearchDocument::sku).containsExactly("WHITE");
        var soldOut=request("phone");soldOut.setHasStock(false);
        var result=search.search(soldOut,PageRequest.of(0,20)).getContent().get(0);
        assertThat(result.skuId()).isEqualTo(blackId);assertThat(result.stockQuantity()).isZero();
        assertThat(documentCount()).isEqualTo(2);
    }

    @Test void variantAddDeleteAndSpuDeleteDoNotLeaveObsoleteSkuDocuments() {
        var color=options.create(new OptionRequest("Color"));var black=value(color,"Black");var white=value(color,"White");
        var id=create("Phone","Mobile",sku("BLACK",1,black));
        var blackId=products.findById(id).variants().get(0).id();
        var whiteId=products.addVariant(id,sku("WHITE",2,white));refresh();
        assertThat(documentCount()).isEqualTo(2);
        products.deleteVariant(id,blackId);refresh();
        assertThat(documentCount()).isEqualTo(1);
        assertThat(search.search(request("phone"),PageRequest.of(0,20)))
                .extracting(SkuSearchDocument::skuId).containsExactly(whiteId);
        products.deleteVariant(id,whiteId);refresh();
        assertThat(documentCount()).isZero();
        products.addVariant(id,sku("AGAIN",3));refresh();
        assertThat(documentCount()).isEqualTo(1);
        products.deleteProduct(id);refresh();
        assertThat(documentCount()).isZero();
    }

    @Test void deletingSharedOptionValuesAndOptionsUpdatesAffectedSkuAttributes() {
        var color=options.create(new OptionRequest("Color"));var black=value(color,"Black");
        var storage=options.create(new OptionRequest("Storage"));var small=value(storage,"128GB");
        create("Phone One","Mobile",sku("ONE",3,black,small));
        create("Phone Two","Mobile",sku("TWO",3,black,small));refresh();
        assertThat(search.search(request(null,"Color","Black"),PageRequest.of(0,20)).getTotalElements()).isEqualTo(2);
        options.deleteValue(color,black);refresh();
        assertThat(search.search(request(null,"Color","Black"),PageRequest.of(0,20))).isEmpty();
        assertThat(search.search(request(null,"Storage","128GB"),PageRequest.of(0,20)).getTotalElements()).isEqualTo(2);
        options.delete(storage);refresh();
        assertThat(search.search(request(null,"Storage","128GB"),PageRequest.of(0,20))).isEmpty();
        assertThat(search.search(request(null),PageRequest.of(0,20)))
                .allSatisfy(document->assertThat(document.attrs()).isEmpty());
    }

    @Test void rollbackDoesNotIndexCreateOrStockChanges() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            create("Rollback keyboard","Not indexed");status.setRollbackOnly();
        });refresh();assertThat(documentCount()).isZero();assertThat(repository.count()).isZero();
        var id=create("Keyboard","Current");var skuId=products.findById(id).variants().get(0).id();
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            products.purchaseProducts(List.of(new ProductPurchaseRequest(skuId,2)));status.setRollbackOnly();
        });refresh();
        assertThat(search.search(request("keyboard"),PageRequest.of(0,20)).getContent().get(0).stockQuantity()).isEqualTo(5);
    }

    @Test void bulkFailureDoesNotUndoCommittedProductAndReindexRecoversIt() {
        doThrow(new DataAccessResourceFailureException("Simulated Elasticsearch outage"))
                .when(operations).save(org.mockito.ArgumentMatchers.<SkuSearchDocument>anyIterable());
        var id=create("Recovery keyboard","Reindex me");
        assertThat(repository.findById(id)).isPresent();refresh();assertThat(documentCount()).isZero();
        // Restore this stub before the maintenance rebuild uses the same bulk save method.
        org.mockito.Mockito.doCallRealMethod().when(operations).save(org.mockito.ArgumentMatchers.<SkuSearchDocument>anyIterable());
        assertThat(search.reindex()).isEqualTo(1);
        assertThat(search.search(request("recovery"),PageRequest.of(0,20)))
                .extracting(SkuSearchDocument::spuId).containsExactly(id);
    }

    @Test void partialBulkItemFailureLeavesDatabaseCommittedAndCanBeRebuilt() {
        var color=options.create(new OptionRequest("Color"));
        var black=value(color,"Black");var white=value(color,"White");
        org.mockito.Mockito.doAnswer(invocation->{
            SkuSearchDocument document=(SkuSearchDocument)invocation.callRealMethod();
            if(!document.sku().equals("BAD-ITEM")) return document;
            // A keyword over Lucene's term limit fails just one bulk item; DB SKU codes remain valid.
            return new SkuSearchDocument(document.skuId(),document.spuId(),"X".repeat(40000),document.slug(),
                    document.skuTitle(),document.description(),document.skuPrice(),
                    document.stockQuantity(),document.hasStock(),document.attrs());
        }).when(mapper).toSearchDocument(org.mockito.ArgumentMatchers.any(com.duong.ecommerce.product.entity.ProductVariant.class));
        var id=create("Partial phone","Mobile",sku("GOOD-ITEM",3,black),sku("BAD-ITEM",3,white));
        assertThat(products.findById(id).variants()).hasSize(2);
        refresh();assertThat(documentCount()).isEqualTo(1);
        org.mockito.Mockito.doCallRealMethod().when(mapper)
                .toSearchDocument(org.mockito.ArgumentMatchers.any(com.duong.ecommerce.product.entity.ProductVariant.class));
        assertThat(search.reindex()).isEqualTo(2);assertThat(documentCount()).isEqualTo(2);
    }

    @Test void reindexAndSpuCountHandleMultipleBatchesAndClearStaleDocuments() {
        var now=java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
        var color=options.create(new OptionRequest("Color"));
        var selected=List.of(value(color,"Black"),value(color,"White"));
        var productRows=new ArrayList<Object[]>();var skuRows=new ArrayList<Object[]>();
        var selectionRows=new ArrayList<Object[]>();
        for(int i=0;i<503;i++) {
            var id=UUID.randomUUID().toString();
            productRows.add(new Object[]{id,"Batch keyboard "+i,"batch-"+i,now,now});
            for(int j=0;j<2;j++) {
                var skuId=UUID.randomUUID().toString();
                skuRows.add(new Object[]{skuId,"BATCH-"+i+"-"+j,BigDecimal.TEN,5,id,now,now});
                selectionRows.add(new Object[]{skuId,selected.get(j)});
            }
        }
        jdbc.batchUpdate("insert into product(id,name,slug,created_at,updated_at) values(?,?,?,?,?)",productRows);
        jdbc.batchUpdate("insert into product_variant(id,sku,price,stock_quantity,product_id,created_at,updated_at) values(?,?,?,?,?,?,?)",skuRows);
        jdbc.batchUpdate("insert into product_variant_option_value(product_variant_id,option_value_id) values(?,?)",selectionRows);
        operations.save(new SkuSearchDocument(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"STALE",
                "stale","Stale keyboard",null,BigDecimal.TEN,1,true,List.of()));
        assertThat(search.reindex()).isEqualTo(1006);
        assertThat(documentCount()).isEqualTo(1006);
        var last=search.search(request("keyboard"),PageRequest.of(5,100));
        assertThat(last.getTotalElements()).isEqualTo(503);assertThat(last.getContent()).hasSize(3);
        jdbc.update("delete from product");assertThat(search.reindex()).isZero();
        assertThat(search.search(request(null),PageRequest.of(0,20))).isEmpty();
    }
}

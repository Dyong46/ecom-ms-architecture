package com.duong.ecommerce.product.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import com.duong.ecommerce.product.entity.Product;
import com.duong.ecommerce.product.integration.search.SkuSearchDocument;
import com.duong.ecommerce.product.mapper.ProductMapper;
import com.duong.ecommerce.product.payload.event.ProductSearchChanged;
import com.duong.ecommerce.product.payload.request.ProductSearchRequest;
import com.duong.ecommerce.product.repository.ProductRepository;
import com.duong.module.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.CollectionUtils;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class ProductSearchService {
    private static final String SPU_GROUPS = "spu_groups";
    private final ElasticsearchOperations elasticsearch;
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public Page<SkuSearchDocument> search(ProductSearchRequest request, Pageable pageable) {
        if (pageable.getOffset() + pageable.getPageSize() > 10_000) {
            throw new BadRequestException("Search pagination cannot exceed 10000 results");
        }
        ensureIndex();
        var criteria = searchCriteria(request);
        var searchPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        var query = NativeQuery.builder().withQuery(criteria)
                .withFieldCollapse(FieldCollapse.of(c -> c.field("spuId")))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                .withSort(s -> s.field(field -> field.field("spuId").order(SortOrder.Asc)))
                .withSort(s -> s.field(field -> field.field("skuId").order(SortOrder.Asc)))
                .withAggregation(SPU_GROUPS, groupAggregation(null))
                .withPageable(searchPage).build();
        var hits = elasticsearch.search(query, SkuSearchDocument.class);
        // hits.total counts SKU documents before collapse. Enumerate distinct SPU buckets instead.
        long total = countSpus(criteria, hits);
        return new PageImpl<>(hits.getSearchHits().stream().map(SearchHit::getContent).toList(), searchPage, total);
    }

    private Query searchCriteria(ProductSearchRequest request) {
        var bool = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
        String keyword = request.getQ() == null ? "" : request.getQ().trim();
        if (keyword.isEmpty()) {
            bool.must(q -> q.matchAll(m -> m));
        } else {
            bool.must(q -> q.bool(b -> b
                    .should(s -> s.multiMatch(m -> m.query(keyword).fields("skuTitle^3", "description")
                            .operator(Operator.And).fuzziness("AUTO")))
                    .should(s -> s.term(t -> t.field("sku").value(keyword).caseInsensitive(true).boost(5f)))
                    .minimumShouldMatch("1")));
        }
        for (var option : request.getOptions()) {
            bool.filter(q -> q.nested(n -> n.path("attrs").scoreMode(ChildScoreMode.None)
                    .query(nq -> nq.bool(b -> b
                            .filter(f -> f.term(t -> t.field("attrs.attrName").value(option.getName())))
                            .filter(f -> f.term(t -> t.field("attrs.attrValue").value(option.getValue())))))));
        }
        if (request.getHasStock() != null) {
            bool.filter(q -> q.term(t -> t.field("hasStock").value(request.getHasStock())));
        }
        return bool.build()._toQuery();
    }

    private Aggregation groupAggregation(Map<String, FieldValue> after) {
        return Aggregation.of(a -> a.composite(c -> {
            c.size(500).sources(List.of(Map.of("spu", CompositeAggregationSource.of(s -> s.terms(t -> t.field("spuId"))))));
            if (after != null) c.after(after);
            return c;
        }));
    }

    private long countSpus(Query criteria, SearchHits<SkuSearchDocument> hits) {
        long count = 0;
        while (true) {
            var groups = groups(hits);
            count += groups.buckets().array().size();
            if (groups.buckets().array().isEmpty() || groups.afterKey() == null || groups.afterKey().isEmpty()) return count;
            var query = NativeQuery.builder().withQuery(criteria).withMaxResults(0)
                    .withAggregation(SPU_GROUPS, groupAggregation(groups.afterKey())).build();
            hits = elasticsearch.search(query, SkuSearchDocument.class);
        }
    }

    private CompositeAggregate groups(SearchHits<SkuSearchDocument> hits) {
        if (!(hits.getAggregations() instanceof ElasticsearchAggregations aggregations)
                || aggregations.get(SPU_GROUPS) == null) {
            throw new IllegalStateException("Search response is missing SPU group counts");
        }
        return aggregations.get(SPU_GROUPS).aggregation().getAggregate().composite();
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void synchronizeProduct(ProductSearchChanged event) {
        try {
            ensureIndex();
            var documents = repository.findById(event.productId())
                    .map(product -> product.getVariants().stream().map(mapper::toSearchDocument).toList())
                    .orElseGet(List::of);
            if (!documents.isEmpty()) elasticsearch.save(documents);
            // Make even recent writes visible to delete-by-query before removing obsolete SKU IDs.
            elasticsearch.indexOps(SkuSearchDocument.class).refresh();
            var stale = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder()
                    .filter(q -> q.term(t -> t.field("spuId").value(event.productId())));
            if (!documents.isEmpty()) {
                stale.mustNot(q -> q.ids(ids -> ids.values(documents.stream().map(SkuSearchDocument::skuId).toList())));
            }
            checkDeletion(elasticsearch.delete(NativeQuery.builder().withQuery(stale.build()._toQuery()).build(),
                    SkuSearchDocument.class));
        } catch (RuntimeException exception) {
            log.warn("SKU search synchronization failed: productId={}, errorType={}; reindex required",
                    event.productId(), exception.getClass().getSimpleName());
        }
    }

    /** Maintenance operation: pause catalog writes/purchases while rebuilding the live SKU index. */
    @Transactional(readOnly = true)
    public long reindex() {
        ensureIndex();
        elasticsearch.indexOps(SkuSearchDocument.class).refresh();
        checkDeletion(elasticsearch.delete(org.springframework.data.elasticsearch.core.query.Query.findAll(),
                SkuSearchDocument.class));
        long indexed = 0;
        Page<Product> page;
        var pageable = PageRequest.of(0, 200, Sort.by("id"));
        do {
            page = repository.findAll(pageable);
            var documents = page.getContent().stream().flatMap(product -> product.getVariants().stream())
                    .map(mapper::toSearchDocument).toList();
            if (!documents.isEmpty()) {
                elasticsearch.save(documents);
                indexed += documents.size();
            }
            pageable = pageable.next();
        } while (page.hasNext());
        elasticsearch.indexOps(SkuSearchDocument.class).refresh();
        return indexed;
    }

    private void checkDeletion(ByQueryResponse deletion) {
        if (deletion.getTimedOut() || deletion.getVersionConflicts() > 0 || deletion.getReasonCancelled() != null
                || !CollectionUtils.isEmpty(deletion.getFailures()) || !CollectionUtils.isEmpty(deletion.getSearchFailures())) {
            throw new IllegalStateException("SKU index cleanup was incomplete; reindex required");
        }
    }

    private synchronized void ensureIndex() {
        var index = elasticsearch.indexOps(SkuSearchDocument.class);
        if (!index.exists()) index.createWithMapping();
    }
}

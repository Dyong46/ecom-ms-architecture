package com.duong.module.web.payload.response;

import com.duong.module.web.payload.filter.PaginationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {
    @Test
    void mapsFirstApiPageToZeroBasedDatabasePage() {
        var request = new PaginationRequest();
        assertEquals(1, request.getPage());
        assertEquals(0, request.toPageable().getPageNumber());
        assertEquals(20, request.toPageable().getPageSize());
        request.setPage(3);
        assertEquals(2, request.toPageable().getPageNumber());
    }

    @Test
    void serializesContentAndOneBasedMetadata() throws Exception {
        var page = new PageImpl<>(List.of("product"), PageRequest.of(1, 10), 45);
        var json = new ObjectMapper().valueToTree(PageResponse.build(page));
        assertEquals("product", json.get("content").get(0).asText());
        assertFalse(json.has("data"));
        assertEquals(2, json.get("page_info").get("number").asInt());
        assertEquals(45, json.get("page_info").get("total_elements").asLong());
    }

    @Test
    void mapsContentAndPreservesPageMetadata() {
        var result = PageResponse.build(new PageImpl<>(List.of(123), PageRequest.of(0, 20), 1),
                Object::toString);
        assertEquals(List.of("123"), result.getContent());
        assertEquals(1, result.getPageInfo().getNumber());
        assertTrue(result.getPageInfo().isLast());
    }
}

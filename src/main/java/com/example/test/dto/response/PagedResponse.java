package com.example.test.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Carries the page of data alongside pagination metadata so clients
 * know the total count and whether more pages exist.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    private List<T> content;

    /** 0-based page number that was requested. */
    private int page;

    /** Number of records per page that was requested. */
    private int size;

    /** Total number of matching records across all pages. */
    private long totalElements;

    /** Total number of pages available. */
    private int totalPages;

    /** Whether this is the last page. */
    private boolean last;
}

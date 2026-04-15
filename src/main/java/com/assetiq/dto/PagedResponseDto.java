package com.assetiq.dto;

import lombok.Data;

import java.util.List;

/**
 * Standard envelope for list endpoints.
 */
@Data
public class PagedResponseDto<T> {
    private long total;
    private int limit;
    private long offset;
    private List<T> items;
}


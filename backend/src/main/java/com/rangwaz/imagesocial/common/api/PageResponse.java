package com.rangwaz.imagesocial.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        long page,
        long size
) {
}

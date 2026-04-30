package com.rangwaz.imagesocial.feature;

import java.util.LinkedHashMap;
import java.util.Map;

public record PostRealtimeMetrics(
        long exposure1h,
        long click1h,
        long detailView1h,
        long positive1h,
        long negative1h,
        long exposure24h,
        long click24h,
        long detailView24h,
        long positive24h,
        long negative24h
) {
    public static PostRealtimeMetrics empty() {
        return new PostRealtimeMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public boolean isEmpty() {
        return exposure1h <= 0
                && click1h <= 0
                && detailView1h <= 0
                && positive1h <= 0
                && negative1h <= 0
                && exposure24h <= 0
                && click24h <= 0
                && detailView24h <= 0
                && positive24h <= 0
                && negative24h <= 0;
    }

    public double ctr1h() {
        return rate(click1h, exposure1h);
    }

    public double detailRate1h() {
        return rate(detailView1h, exposure1h);
    }

    public double positiveRate1h() {
        return rate(positive1h, exposure1h);
    }

    public double ctr24h() {
        return rate(click24h, exposure24h);
    }

    public double detailRate24h() {
        return rate(detailView24h, exposure24h);
    }

    public double positiveRate24h() {
        return rate(positive24h, exposure24h);
    }

    public double negativeRate24h() {
        return rate(negative24h, exposure24h);
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exposure_1h", exposure1h);
        payload.put("click_1h", click1h);
        payload.put("detail_view_1h", detailView1h);
        payload.put("positive_1h", positive1h);
        payload.put("negative_1h", negative1h);
        payload.put("ctr_1h", ctr1h());
        payload.put("detail_rate_1h", detailRate1h());
        payload.put("positive_rate_1h", positiveRate1h());
        payload.put("exposure_24h", exposure24h);
        payload.put("click_24h", click24h);
        payload.put("detail_view_24h", detailView24h);
        payload.put("positive_24h", positive24h);
        payload.put("negative_24h", negative24h);
        payload.put("ctr_24h", ctr24h());
        payload.put("detail_rate_24h", detailRate24h());
        payload.put("positive_rate_24h", positiveRate24h());
        payload.put("negative_rate_24h", negativeRate24h());
        return payload;
    }

    private double rate(long numerator, long denominator) {
        if (numerator <= 0L || denominator <= 0L) {
            return 0.0d;
        }
        return numerator / (double) denominator;
    }
}

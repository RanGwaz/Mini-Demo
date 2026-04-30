package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.UserEvent;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineExperimentRow;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineSourceRow;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineSummaryRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserEventMapper extends BaseMapper<UserEvent> {

    @Select("""
            <script>
            SELECT * FROM user_events
            WHERE user_id = #{userId}
              AND event_type IN ('POST_LIKE', 'POST_FAVORITE', 'POST_CREATE')
            ORDER BY created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<UserEvent> selectRecentInterestEvents(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              COALESCE(SUM(CASE WHEN event_type = 'FEED_EXPOSURE' THEN 1 ELSE 0 END), 0) AS exposure_count,
              COALESCE(SUM(CASE WHEN event_type = 'POST_CLICK' THEN 1 ELSE 0 END), 0) AS click_count,
              COALESCE(SUM(CASE WHEN event_type = 'POST_DETAIL_VIEW' THEN 1 ELSE 0 END), 0) AS detail_view_count,
              COALESCE(SUM(CASE WHEN event_type = 'POST_LIKE' THEN 1 ELSE 0 END), 0) AS like_count,
              COALESCE(SUM(CASE WHEN event_type = 'POST_FAVORITE' THEN 1 ELSE 0 END), 0) AS favorite_count,
              COALESCE(SUM(CASE WHEN event_type = 'POST_COMMENT' THEN 1 ELSE 0 END), 0) AS comment_count,
              COALESCE(SUM(CASE WHEN event_type = 'POST_SHARE' THEN 1 ELSE 0 END), 0) AS share_count,
              COALESCE(SUM(CASE WHEN event_type IN ('NOT_INTERESTED', 'POST_NEGATIVE_FEEDBACK', 'POST_HIDE') THEN 1 ELSE 0 END), 0) AS negative_count,
              COALESCE(COUNT(DISTINCT CASE WHEN event_type = 'FEED_EXPOSURE' THEN request_id END), 0) AS request_uv,
              COALESCE(COUNT(DISTINCT CASE WHEN event_type = 'FEED_EXPOSURE' THEN user_id END), 0) AS exposure_user_uv,
              COALESCE(AVG(CASE WHEN event_type = 'POST_DETAIL_VIEW' AND dwell_ms IS NOT NULL THEN dwell_ms END), 0) AS avg_dwell_ms
            FROM user_events
            WHERE created_at <![CDATA[>=]]> #{fromTime}
              AND created_at <![CDATA[<]]> #{toTime}
              <if test="surface != null and surface != ''">
                AND surface = #{surface}
              </if>
              <if test="userId != null">
                AND user_id = #{userId}
              </if>
            </script>
            """)
    FeedOnlineSummaryRow selectFeedOnlineSummary(@Param("fromTime") LocalDateTime fromTime,
                                                 @Param("toTime") LocalDateTime toTime,
                                                 @Param("surface") String surface,
                                                 @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT
              COALESCE(NULLIF(e.recall_source, ''), 'unknown') AS recall_source,
              COUNT(*) AS exposure_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events ec
                  WHERE ec.user_id = e.user_id
                    AND ec.target_id = e.target_id
                    AND ec.event_type = 'POST_CLICK'
                    AND ec.created_at <![CDATA[>=]]> e.created_at
                    AND ec.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS click_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events ed
                  WHERE ed.user_id = e.user_id
                    AND ed.target_id = e.target_id
                    AND ed.event_type = 'POST_DETAIL_VIEW'
                    AND ed.created_at <![CDATA[>=]]> e.created_at
                    AND ed.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS detail_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events el
                  WHERE el.user_id = e.user_id
                    AND el.target_id = e.target_id
                    AND el.event_type = 'POST_LIKE'
                    AND el.created_at <![CDATA[>=]]> e.created_at
                    AND el.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS like_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events ef
                  WHERE ef.user_id = e.user_id
                    AND ef.target_id = e.target_id
                    AND ef.event_type = 'POST_FAVORITE'
                    AND ef.created_at <![CDATA[>=]]> e.created_at
                    AND ef.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS favorite_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events en
                  WHERE en.user_id = e.user_id
                    AND en.target_id = e.target_id
                    AND en.event_type IN ('NOT_INTERESTED', 'POST_NEGATIVE_FEEDBACK', 'POST_HIDE')
                    AND en.created_at <![CDATA[>=]]> e.created_at
                    AND en.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS negative_through_count
            FROM user_events e
            WHERE e.event_type = 'FEED_EXPOSURE'
              AND e.created_at <![CDATA[>=]]> #{fromTime}
              AND e.created_at <![CDATA[<]]> #{toTime}
              <if test="surface != null and surface != ''">
                AND e.surface = #{surface}
              </if>
              <if test="userId != null">
                AND e.user_id = #{userId}
              </if>
            GROUP BY COALESCE(NULLIF(e.recall_source, ''), 'unknown')
            ORDER BY exposure_count DESC
            LIMIT #{limit}
            </script>
            """)
    List<FeedOnlineSourceRow> selectFeedOnlineSourceRows(@Param("fromTime") LocalDateTime fromTime,
                                                         @Param("toTime") LocalDateTime toTime,
                                                         @Param("surface") String surface,
                                                         @Param("userId") Long userId,
                                                         @Param("attributionHours") int attributionHours,
                                                         @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              COALESCE(NULLIF(e.experiment_id, ''), 'no_exp') AS experiment_id,
              COUNT(*) AS exposure_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events ec
                  WHERE ec.user_id = e.user_id
                    AND ec.target_id = e.target_id
                    AND ec.event_type = 'POST_CLICK'
                    AND ec.created_at <![CDATA[>=]]> e.created_at
                    AND ec.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS click_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events ed
                  WHERE ed.user_id = e.user_id
                    AND ed.target_id = e.target_id
                    AND ed.event_type = 'POST_DETAIL_VIEW'
                    AND ed.created_at <![CDATA[>=]]> e.created_at
                    AND ed.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS detail_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events el
                  WHERE el.user_id = e.user_id
                    AND el.target_id = e.target_id
                    AND el.event_type = 'POST_LIKE'
                    AND el.created_at <![CDATA[>=]]> e.created_at
                    AND el.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS like_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events ef
                  WHERE ef.user_id = e.user_id
                    AND ef.target_id = e.target_id
                    AND ef.event_type = 'POST_FAVORITE'
                    AND ef.created_at <![CDATA[>=]]> e.created_at
                    AND ef.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS favorite_through_count,
              SUM(CASE WHEN EXISTS (
                  SELECT 1
                  FROM user_events en
                  WHERE en.user_id = e.user_id
                    AND en.target_id = e.target_id
                    AND en.event_type IN ('NOT_INTERESTED', 'POST_NEGATIVE_FEEDBACK', 'POST_HIDE')
                    AND en.created_at <![CDATA[>=]]> e.created_at
                    AND en.created_at <![CDATA[<]]> DATE_ADD(e.created_at, INTERVAL #{attributionHours} HOUR)
              ) THEN 1 ELSE 0 END) AS negative_through_count
            FROM user_events e
            WHERE e.event_type = 'FEED_EXPOSURE'
              AND e.created_at <![CDATA[>=]]> #{fromTime}
              AND e.created_at <![CDATA[<]]> #{toTime}
              <if test="surface != null and surface != ''">
                AND e.surface = #{surface}
              </if>
              <if test="userId != null">
                AND e.user_id = #{userId}
              </if>
            GROUP BY COALESCE(NULLIF(e.experiment_id, ''), 'no_exp')
            ORDER BY exposure_count DESC
            LIMIT #{limit}
            </script>
            """)
    List<FeedOnlineExperimentRow> selectFeedOnlineExperimentRows(@Param("fromTime") LocalDateTime fromTime,
                                                                 @Param("toTime") LocalDateTime toTime,
                                                                 @Param("surface") String surface,
                                                                 @Param("userId") Long userId,
                                                                 @Param("attributionHours") int attributionHours,
                                                                 @Param("limit") int limit);
}

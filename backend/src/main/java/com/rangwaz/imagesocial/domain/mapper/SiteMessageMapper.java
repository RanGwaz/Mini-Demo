package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.SiteMessage;
import com.rangwaz.imagesocial.message.MessageConversationRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SiteMessageMapper extends BaseMapper<SiteMessage> {

    @Select("""
            SELECT COUNT(*)
            FROM site_messages
            WHERE recipient_id = #{userId}
              AND message_kind = 'DIRECT'
              AND read_at IS NULL
            """)
    long countUnreadDirect(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM site_messages
            WHERE recipient_id = #{userId}
              AND message_kind != 'DIRECT'
              AND read_at IS NULL
            """)
    long countUnreadNotifications(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM (
              SELECT
                CASE WHEN m.sender_id = #{userId} THEN m.recipient_id ELSE m.sender_id END AS peer_id
              FROM site_messages m
              INNER JOIN users u
                ON u.id = CASE WHEN m.sender_id = #{userId} THEN m.recipient_id ELSE m.sender_id END
              WHERE m.message_kind = 'DIRECT'
                AND (m.sender_id = #{userId} OR m.recipient_id = #{userId})
                <if test='keyword != null and keyword != ""'>
                  AND (
                    u.nickname LIKE CONCAT('%', #{keyword}, '%')
                    OR u.username LIKE CONCAT('%', #{keyword}, '%')
                    OR m.content LIKE CONCAT('%', #{keyword}, '%')
                  )
                </if>
              GROUP BY peer_id
            ) conv_count
            </script>
            """)
    long countConversations(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT
              conv_rows.peer_id AS peerId,
              SUBSTRING_INDEX(GROUP_CONCAT(conv_rows.content ORDER BY conv_rows.created_at DESC, conv_rows.id DESC SEPARATOR '||__MSG__||'), '||__MSG__||', 1) AS lastMessage,
              MAX(conv_rows.created_at) AS lastMessageAt,
              SUM(CASE WHEN conv_rows.recipient_id = #{userId} AND conv_rows.read_at IS NULL THEN 1 ELSE 0 END) AS unreadCount,
              COUNT(*) AS messageCount
            FROM (
              SELECT
                m.id,
                m.sender_id,
                m.recipient_id,
                m.content,
                m.read_at,
                m.created_at,
                CASE WHEN m.sender_id = #{userId} THEN m.recipient_id ELSE m.sender_id END AS peer_id
              FROM site_messages m
              INNER JOIN users u
                ON u.id = CASE WHEN m.sender_id = #{userId} THEN m.recipient_id ELSE m.sender_id END
              WHERE m.message_kind = 'DIRECT'
                AND (m.sender_id = #{userId} OR m.recipient_id = #{userId})
                <if test='keyword != null and keyword != ""'>
                  AND (
                    u.nickname LIKE CONCAT('%', #{keyword}, '%')
                    OR u.username LIKE CONCAT('%', #{keyword}, '%')
                    OR m.content LIKE CONCAT('%', #{keyword}, '%')
                  )
                </if>
            ) conv_rows
            GROUP BY conv_rows.peer_id
            ORDER BY lastMessageAt DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MessageConversationRow> selectConversationRows(@Param("userId") Long userId,
                                                        @Param("keyword") String keyword,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM site_messages
            WHERE message_kind = 'DIRECT'
              AND (
                (sender_id = #{userId} AND recipient_id = #{peerId})
                OR (sender_id = #{peerId} AND recipient_id = #{userId})
              )
            """)
    long countThread(@Param("userId") Long userId, @Param("peerId") Long peerId);

    @Select("""
            SELECT *
            FROM (
              SELECT *
              FROM site_messages
              WHERE message_kind = 'DIRECT'
                AND (
                  (sender_id = #{userId} AND recipient_id = #{peerId})
                  OR (sender_id = #{peerId} AND recipient_id = #{userId})
                )
              ORDER BY created_at DESC, id DESC
              LIMIT #{limit} OFFSET #{offset}
            ) thread_rows
            ORDER BY thread_rows.created_at ASC, thread_rows.id ASC
            """)
    List<SiteMessage> selectThread(@Param("userId") Long userId,
                                   @Param("peerId") Long peerId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM site_messages
            WHERE recipient_id = #{userId}
              AND message_kind != 'DIRECT'
              <if test='type == "interaction"'>
                AND message_kind = 'INTERACTION'
              </if>
              <if test='type == "system"'>
                AND message_kind = 'SYSTEM'
              </if>
            </script>
            """)
    long countNotifications(@Param("userId") Long userId, @Param("type") String type);

    @Select("""
            <script>
            SELECT *
            FROM site_messages
            WHERE recipient_id = #{userId}
              AND message_kind != 'DIRECT'
              <if test='type == "interaction"'>
                AND message_kind = 'INTERACTION'
              </if>
              <if test='type == "system"'>
                AND message_kind = 'SYSTEM'
              </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SiteMessage> selectNotifications(@Param("userId") Long userId,
                                          @Param("type") String type,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    @Update("""
            UPDATE site_messages
            SET read_at = COALESCE(read_at, NOW())
            WHERE recipient_id = #{userId}
              AND sender_id = #{peerId}
              AND message_kind = 'DIRECT'
              AND read_at IS NULL
            """)
    int markThreadRead(@Param("userId") Long userId, @Param("peerId") Long peerId);

    @Update("""
            UPDATE site_messages
            SET read_at = COALESCE(read_at, NOW())
            WHERE id = #{messageId}
              AND recipient_id = #{userId}
              AND read_at IS NULL
            """)
    int markMessageRead(@Param("userId") Long userId, @Param("messageId") Long messageId);

    @Update("""
            UPDATE site_messages
            SET read_at = COALESCE(read_at, NOW())
            WHERE recipient_id = #{userId}
              AND message_kind = 'DIRECT'
              AND read_at IS NULL
            """)
    int markAllDirectRead(@Param("userId") Long userId);

    @Update("""
            UPDATE site_messages
            SET read_at = COALESCE(read_at, NOW())
            WHERE recipient_id = #{userId}
              AND message_kind != 'DIRECT'
              AND read_at IS NULL
            """)
    int markAllNotificationsRead(@Param("userId") Long userId);
}

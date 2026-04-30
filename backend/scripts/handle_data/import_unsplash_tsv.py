#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导入 Unsplash TSV 文件到 MySQL（硬编码参数版本，已修复空值问题）
需要安装：pip install pymysql
"""

import csv
from datetime import datetime
import pymysql

# ========== 硬编码配置 ==========
PHOTOS_TSV_PATH = "../unslashDataSet/photos.tsv"
KEYWORDS_TSV_PATH = "../unslashDataSet/keywords.tsv"

MYSQL_HOST = "127.0.0.1"
MYSQL_PORT = 3306
MYSQL_USER = "root"
MYSQL_PASSWORD = "root123456"
MYSQL_DATABASE = "image_social"

CLEAR_TABLES = True   # 是否清空现有数据
# ================================

# BCrypt("123456") 的哈希，用于模拟用户密码
DEFAULT_PASSWORD_HASH = "$2a$10$Dow1e4A6f4Bf2b5vQ4hTGO9E6Mc8V4xw5lP1vCwD0N2V6WmPZ5X9K"


def connect_db():
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset='utf8mb4',
        autocommit=False
    )


def truncate_tables(conn):
    """清空相关表（按依赖顺序）"""
    tables = [
        'post_assets', 'post_likes', 'post_favorites', 'post_comments',
        'user_follows', 'user_blocks', 'post_negative_feedbacks',
        'content_reports', 'user_events', 'posts', 'users'
    ]
    with conn.cursor() as cur:
        for table in tables:
            cur.execute(f"TRUNCATE TABLE {table}")
    conn.commit()
    print("已清空相关表")


def safe_int(val):
    """安全转换为整数，如果为空或无效返回 0"""
    if val is None or str(val).strip() == '':
        return 0
    try:
        return int(val)
    except:
        return 0


def safe_float(val):
    """安全转换为浮点数，如果为空或无效返回 0.0"""
    if val is None or str(val).strip() == '':
        return 0.0
    try:
        return float(val)
    except:
        return 0.0


def import_photos(conn):
    """导入 photos.tsv，同时插入 users 和 posts"""
    user_map = {}  # username -> user_id
    post_map = {}  # photo_id -> post_id

    user_insert_sql = """
        INSERT IGNORE INTO users (
            username, password_hash, nickname, avatar_url, bio,
            roles, status, created_at, updated_at, deleted, version
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    post_insert_sql = """
        INSERT INTO posts (
            author_id, title, content, tags, cover_url, visibility, audit_status,
            like_count, favorite_count, comment_count, view_count, hot_score,
            created_at, updated_at, deleted, version
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    asset_insert_sql = """
        INSERT INTO post_assets (
            post_id, object_key, file_url, file_type, sort_order,
            created_at, deleted, version
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
    """

    with open(PHOTOS_TSV_PATH, 'r', encoding='utf-8') as f:
        reader = csv.reader(f, delimiter='\t')
        header = next(reader)
        idx = {name: i for i, name in enumerate(header)}

        with conn.cursor() as cur:
            post_count = 0
            for row in reader:
                # 提取字段，缺失的字段用空字符串或0代替
                photo_id = row[idx['photo_id']]
                photographer_username = row[idx['photographer_username']]
                photographer_first_name = row[idx.get('photographer_first_name', -1)] if 'photographer_first_name' in idx else ''
                photographer_last_name = row[idx.get('photographer_last_name', -1)] if 'photographer_last_name' in idx else ''
                photo_image_url = row[idx['photo_image_url']]
                photo_submitted_at = row[idx['photo_submitted_at']]
                photo_description = row[idx.get('photo_description', -1)] if 'photo_description' in idx else ''
                stats_views = safe_int(row[idx.get('stats_views', -1)]) if 'stats_views' in idx else 0
                stats_downloads = safe_int(row[idx.get('stats_downloads', -1)]) if 'stats_downloads' in idx else 0

                # 构建用户信息
                nickname = f"{photographer_first_name} {photographer_last_name}".strip() or photographer_username
                bio = "Unsplash photographer"
                avatar_url = f"https://unsplash.com/@{photographer_username}"

                # 插入用户（如果不存在）
                cur.execute(user_insert_sql, (
                    photographer_username, DEFAULT_PASSWORD_HASH, nickname, avatar_url, bio,
                    'ROLE_USER', 'ACTIVE', datetime.now(), datetime.now(), 0, 0
                ))
                if cur.lastrowid:
                    user_id = cur.lastrowid
                    user_map[photographer_username] = user_id
                else:
                    cur.execute("SELECT id FROM users WHERE username=%s", (photographer_username,))
                    row_user = cur.fetchone()
                    if row_user:
                        user_id = row_user[0]
                        user_map[photographer_username] = user_id
                    else:
                        continue

                # 处理帖子内容
                title = photo_description[:128] if photo_description else f"Photo by {photographer_username}"
                content = (photo_description or '')[:1024]
                cover_url = photo_image_url
                created_at_str = photo_submitted_at
                if created_at_str:
                    try:
                        created_at = datetime.fromisoformat(created_at_str.replace('Z', '+00:00'))
                    except:
                        created_at = datetime.now()
                else:
                    created_at = datetime.now()

                # 插入帖子
                cur.execute(post_insert_sql, (
                    user_id, title, content, '', cover_url,
                    'PUBLIC', 'APPROVED',
                    0, 0, 0, stats_views, 0.0,
                    created_at, created_at, 0, 0
                ))
                post_id = cur.lastrowid
                post_map[photo_id] = post_id

                # 插入资产（主图）
                object_key = f"unsplash/{photo_id}"
                cur.execute(asset_insert_sql, (
                    post_id, object_key, cover_url, 'IMAGE', 0,
                    created_at, 0, 0
                ))

                post_count += 1
                if post_count % 1000 == 0:
                    conn.commit()
                    print(f"已导入 {post_count} 个帖子")

            conn.commit()
            print(f"共导入 {post_count} 个帖子")
            return user_map, post_map


def update_keywords(conn, post_map):
    """从 keywords.tsv 读取关键词并更新 posts.tags"""
    photo_keywords = {}
    with open(KEYWORDS_TSV_PATH, 'r', encoding='utf-8') as f:
        reader = csv.reader(f, delimiter='\t')
        header = next(reader)
        idx = {name: i for i, name in enumerate(header)}
        for row in reader:
            try:
                photo_id = row[idx['photo_id']]
                keyword = row[idx['keyword']]
                conf1 = safe_float(row[idx['ai_service_1_confidence']]) if 'ai_service_1_confidence' in idx else 0.0
                conf2 = safe_float(row[idx['ai_service_2_confidence']]) if 'ai_service_2_confidence' in idx else 0.0
                confidence = max(conf1, conf2)
                if photo_id not in photo_keywords:
                    photo_keywords[photo_id] = []
                photo_keywords[photo_id].append((keyword, confidence))
            except Exception as e:
                print(f"跳过异常行：{row}，错误：{e}")
                continue

    update_sql = "UPDATE posts SET tags = %s WHERE id = %s"
    with conn.cursor() as cur:
        updated = 0
        for photo_id, kw_list in photo_keywords.items():
            if photo_id not in post_map:
                continue
            post_id = post_map[photo_id]
            kw_list.sort(key=lambda x: x[1], reverse=True)
            top_kw = [kw for kw, _ in kw_list[:5]]
            tags_str = ','.join(top_kw)
            cur.execute(update_sql, (tags_str, post_id))
            updated += 1
            if updated % 5000 == 0:
                conn.commit()
                print(f"已更新 {updated} 个帖子的标签")
        conn.commit()
        print(f"共更新 {updated} 个帖子的标签")


def main():
    conn = connect_db()
    if CLEAR_TABLES:
        truncate_tables(conn)

    user_map, post_map = import_photos(conn)
    update_keywords(conn, post_map)

    conn.close()
    print("全部导入完成")


if __name__ == '__main__':
    main()
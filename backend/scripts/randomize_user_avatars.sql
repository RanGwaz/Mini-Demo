USE image_social;

UPDATE users
SET avatar_url = CONCAT(
  'https://api.dicebear.com/7.x/adventurer/svg?seed=',
  LOWER(REPLACE(UUID(), '-', ''))
);

SELECT COUNT(*) AS updated_users FROM users;

SELECT id, nickname, avatar_url
FROM users
ORDER BY id
LIMIT 12;

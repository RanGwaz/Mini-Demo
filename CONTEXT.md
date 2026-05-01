# Domain Context

## Social Publishing Model

- Post: the core user-created content item. A post can be text-only or include one or more media assets.
- Asset: an optional image attachment for a post. Text-only posts are valid with zero assets.
- Channel: a platform-defined primary content lane. Each post belongs to one channel, stored through `topic_path` and `topic_cluster_key`.
- Tag: a user-facing topic label on a post. Tags are searchable and multi-value, but they are not the same thing as a channel.
- Feed Mode: the viewer scope for a feed request, such as recommended, following, or friends.

## Channel And Tag Rule

Channels answer "where does this post live in the product?" Tags answer "what is this post about?". Channel labels should not be copied into the post tag list during publishing.

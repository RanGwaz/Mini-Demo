type MediaAssetLike = {
  fileUrl?: string | null
  thumbUrl?: string | null
  url?: string | null
}

type PostMediaLike = {
  assets?: MediaAssetLike[] | null
  images?: MediaAssetLike[] | null
  coverUrl?: string | null
  thumbUrl?: string | null
}

export const DEFAULT_IMAGE_PLACEHOLDER = '/auto_picture.png'

export function normalizeMediaUrl(url?: string | null) {
  if (!url) return ''
  return String(url).replace('http://localhost:9000', '/minio-img')
}

export function isRealMediaUrl(url?: string | null) {
  const value = String(url || '').trim().toLowerCase()
  if (!value) return false
  if (value.endsWith('/auto_picture.png') || value.endsWith('auto_picture.png')) return false
  if (value.includes('placehold.co')) return false
  if (value.includes('/api/media/placeholders/')) return false
  return true
}

export function getPostMediaAssets(post?: PostMediaLike | null): MediaAssetLike[] {
  if (!post) return []
  const assetList = (post.assets || []).filter((asset) => isRealMediaUrl(asset.thumbUrl) || isRealMediaUrl(asset.fileUrl) || isRealMediaUrl(asset.url))
  if (assetList.length > 0) return assetList
  const imageList = (post.images || []).filter((asset) => isRealMediaUrl(asset.thumbUrl) || isRealMediaUrl(asset.fileUrl) || isRealMediaUrl(asset.url))
  if (imageList.length > 0) return imageList
  if (isRealMediaUrl(post.thumbUrl) || isRealMediaUrl(post.coverUrl)) {
    return [{ thumbUrl: post.thumbUrl, fileUrl: post.coverUrl }]
  }
  return []
}

export function hasPostMedia(post?: PostMediaLike | null) {
  return getPostMediaAssets(post).length > 0
}

export function getPostMediaCandidates(post?: PostMediaLike | null) {
  const candidates: string[] = []
  const seen = new Set<string>()
  for (const asset of getPostMediaAssets(post)) {
    for (const item of [asset.thumbUrl, asset.fileUrl, asset.url]) {
      if (!isRealMediaUrl(item)) continue
      const normalized = normalizeMediaUrl(item)
      if (!normalized || seen.has(normalized)) continue
      seen.add(normalized)
      candidates.push(normalized)
    }
  }
  return candidates
}

export function getPostMediaUrl(post?: PostMediaLike | null, index = 0) {
  const asset = getPostMediaAssets(post)[index]
  return normalizeMediaUrl(asset?.thumbUrl || asset?.fileUrl || asset?.url)
}

export function getPostFullMediaUrl(post?: PostMediaLike | null, index = 0) {
  const asset = getPostMediaAssets(post)[index]
  return normalizeMediaUrl(asset?.fileUrl || asset?.thumbUrl || asset?.url)
}

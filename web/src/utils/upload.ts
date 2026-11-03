import { apiRequest } from '@/api/client'
import type { MediaUploadVO } from '@/types/api'

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

export function validateImage(file: File): void {
  if (!IMAGE_TYPES.has(file.type)) {
    throw new Error('仅支持 JPG、PNG 或 WebP 图片')
  }
  if (file.size <= 0 || file.size > MAX_IMAGE_BYTES) {
    throw new Error('单张图片不能超过 10 MB')
  }
}

async function sendToTicket(ticket: MediaUploadVO, file: File): Promise<void> {
  const response = await fetch(ticket.uploadUrl, {
    method: ticket.httpMethod,
    headers: ticket.headers,
    body: file,
  })
  if (!response.ok) throw new Error('图片上传未完成，请重试')
}

export async function uploadListingImage(
  listingId: string,
  file: File,
  sortOrder: number,
): Promise<string> {
  validateImage(file)
  const ticket = await apiRequest<MediaUploadVO>(`/listings/${listingId}/image-uploads`, {
    method: 'POST',
    body: JSON.stringify({ contentType: file.type, sizeBytes: file.size }),
  })
  await sendToTicket(ticket, file)
  const registered = await apiRequest<{ id: string }>(`/listings/${listingId}/images`, {
    method: 'POST',
    body: JSON.stringify({
      objectKey: ticket.objectKey,
      contentType: file.type,
      sizeBytes: file.size,
      sortOrder,
    }),
  })
  return registered.id
}

export async function uploadAvatar(file: File): Promise<void> {
  validateImage(file)
  const ticket = await apiRequest<MediaUploadVO>('/users/me/avatar-uploads', {
    method: 'POST',
    body: JSON.stringify({ contentType: file.type, sizeBytes: file.size }),
  })
  await sendToTicket(ticket, file)
  await apiRequest<void>('/users/me/avatar', {
    method: 'PUT',
    body: JSON.stringify({
      objectKey: ticket.objectKey,
      contentType: file.type,
      sizeBytes: file.size,
    }),
  })
}

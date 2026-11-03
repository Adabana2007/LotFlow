import { clearSession, readSession, writeSession } from './session'

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  [key: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `请求失败 (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  accessTokenExpiresAt?: string
  sessionId: string
}

let refreshPromise: Promise<boolean> | null = null

async function readProblem(response: Response): Promise<ProblemDetail> {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('json')) {
    try {
      return (await response.json()) as ProblemDetail
    } catch {
      return { status: response.status, detail: response.statusText }
    }
  }
  return { status: response.status, detail: response.statusText || '请求失败' }
}

async function refreshSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise
  const session = readSession()
  if (!session?.refreshToken) return false

  refreshPromise = (async () => {
    try {
      const response = await fetch('/api/v1/auth/tokens/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      })
      if (!response.ok) {
        clearSession()
        return false
      }
      const tokens = (await response.json()) as TokenResponse
      writeSession({ ...tokens, sessionId: tokens.sessionId || session.sessionId })
      return true
    } catch {
      return false
    } finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

function statusFallback(status: number): string {
  if (status === 0) return '暂时无法连接服务，请稍后重试'
  if (status === 401 || status === 403) return '登录状态已失效，请重新登录'
  if (status === 404) return '没有找到相关内容'
  if (status === 409) return '当前状态已经变化，请刷新后再试'
  if (status === 429) return '操作太频繁，请稍后重试'
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  return '暂时无法完成操作，请稍后重试'
}

function technicalMessage(message: string): boolean {
  return /internal server error|failed to fetch|networkerror|load failed|unexpected token/i.test(message)
}

export async function apiRequestWithMeta<T>(
  path: string,
  init: RequestInit = {},
  retryOnUnauthorized = true,
): Promise<{ data: T; response: Response }> {
  const headers = new Headers(init.headers)
  const session = readSession()
  if (session?.accessToken) headers.set('Authorization', `Bearer ${session.accessToken}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, { detail: statusFallback(0) })
  }
  if (response.status === 401 && retryOnUnauthorized && (await refreshSession())) {
    return apiRequestWithMeta<T>(path, init, false)
  }
  if (!response.ok) throw new ApiError(response.status, await readProblem(response))
  if (response.status === 204 || response.status === 205) {
    return { data: undefined as T, response }
  }
  // 201/202 在创建资源或受理异步任务时可以只有状态码和 Location，空响应不能被当作损坏的 JSON。
  const text = await response.text()
  if (!text) return { data: undefined as T, response }
  return { data: JSON.parse(text) as T, response }
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  retryOnUnauthorized = true,
): Promise<T> {
  return (await apiRequestWithMeta<T>(path, init, retryOnUnauthorized)).data
}

/**
 * 打开 SSE 响应流；鉴权和 401 刷新与普通请求一致，调用方自行读取 response.body。
 * 流建立后服务端错误以事件形式到达，不会体现在状态码上。
 */
export async function apiStream(
  path: string,
  init: RequestInit = {},
  retryOnUnauthorized = true,
): Promise<Response> {
  const headers = new Headers(init.headers)
  const session = readSession()
  if (session?.accessToken) headers.set('Authorization', `Bearer ${session.accessToken}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  // 同时接受 JSON：会话不存在等错误会在建立流之前以 ProblemDetail 返回。
  headers.set('Accept', 'text/event-stream, application/json')

  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, { detail: statusFallback(0) })
  }
  if (response.status === 401 && retryOnUnauthorized && (await refreshSession())) {
    return apiStream(path, init, false)
  }
  if (!response.ok) throw new ApiError(response.status, await readProblem(response))
  return response
}

export function createIdempotencyKey(scope: string): string {
  return `${scope}:${crypto.randomUUID()}`
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status >= 500 || technicalMessage(error.message) ? statusFallback(error.status) : error.message
  }
  if (error instanceof Error) {
    return technicalMessage(error.message) ? statusFallback(0) : error.message
  }
  return '发生未知错误，请稍后重试'
}

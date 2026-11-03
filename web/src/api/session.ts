export interface StoredSession {
  accessToken: string
  refreshToken: string
  accessTokenExpiresAt?: string
  sessionId: string
}

const SESSION_KEY = 'lotflow.session'

export function readSession(): StoredSession | null {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    const value = JSON.parse(raw) as Partial<StoredSession>
    if (!value.accessToken || !value.refreshToken) return null
    return {
      accessToken: value.accessToken,
      refreshToken: value.refreshToken,
      accessTokenExpiresAt: value.accessTokenExpiresAt,
      sessionId: value.sessionId || '',
    }
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function writeSession(session: StoredSession): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY)
}

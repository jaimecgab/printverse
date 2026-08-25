export type UserRole = 'ADMIN' | 'OPERATOR'

export interface AuthUser {
  username: string
  displayName: string
  role: UserRole
}

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthSession {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: AuthUser
}

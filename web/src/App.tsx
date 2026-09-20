import { useEffect, useState } from 'react'
import { apiBaseUrl } from './api-base-url'
import { createLiffSession } from './auth/liff-session'
import { configuredLiffId } from './auth/liff-config'
import type { AuthSession } from './auth/session'
import { AdminInventoryPage } from './inventory/AdminInventoryPage'
import './App.css'
import './inventory/AdminInventoryPage.css'

type HealthState = 'checking' | 'available' | 'unavailable'

function App() {
  const [healthState, setHealthState] = useState<HealthState>('checking')
  const [session, setSession] = useState<AuthSession | null>(null)
  const [signInError, setSignInError] = useState<string | null>(null)
  const [signingIn, setSigningIn] = useState(false)

  useEffect(() => {
    async function checkApiHealth() {
      try {
        const response = await fetch(`${apiBaseUrl}/actuator/health`)
        setHealthState(response.ok ? 'available' : 'unavailable')
      } catch {
        setHealthState('unavailable')
      }
    }

    void checkApiHealth()
  }, [])

  async function signIn() {
    setSigningIn(true)
    setSignInError(null)
    try {
      const nextSession = await createLiffSession()
      setSession(nextSession)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'LINE sign-in could not be completed.'
      if (message !== 'Redirecting to LINE sign-in.') setSignInError(message)
    } finally {
      setSigningIn(false)
    }
  }

  if (session?.user.role === 'ADMIN') {
    return <AdminInventoryPage session={session} />
  }

  return (
    <main className="app-shell">
      <p className="eyebrow">AU-Van platform</p>
      <h1>Admin portal</h1>
      <p className="description">
        Sign in with your approved LINE account to manage routes, vans, seat layouts,
        and scheduled trips.
      </p>
      {!session && (!configuredLiffId() ? <p className="setup-note">Add your LIFF ID to <code>web/.env</code> before signing in.</p> : <button className="button" disabled={signingIn} onClick={() => void signIn()}>{signingIn ? 'Signing in…' : 'Sign in with LINE'}</button>)}
      {signInError && <p className="sign-in-error" role="alert">{signInError}</p>}
      {session?.user.role === 'STUDENT' && <p className="sign-in-error">Your account is signed in but does not have administrator access.</p>}
      <section className="health-card" aria-live="polite">
        <span className={`status-dot ${healthState}`} aria-hidden="true" />
        <div>
          <p className="status-label">API health</p>
          <p className="status-value">
            {healthState === 'checking' && 'Checking local API…'}
            {healthState === 'available' && 'Available'}
            {healthState === 'unavailable' && 'Unavailable — start the API on port 8080'}
          </p>
        </div>
      </section>
    </main>
  )
}

export default App

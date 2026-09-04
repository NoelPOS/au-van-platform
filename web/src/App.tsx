import { useEffect, useState } from 'react'
import './App.css'

type HealthState = 'checking' | 'available' | 'unavailable'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

function App() {
  const [healthState, setHealthState] = useState<HealthState>('checking')

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

  return (
    <main className="app-shell">
      <p className="eyebrow">AU-Van platform</p>
      <h1>Local development environment</h1>
      <p className="description">
        React is connected to the Spring Boot API boundary. Booking features will
        be introduced through focused delivery slices.
      </p>
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

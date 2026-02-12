import React from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from '@tanstack/react-router'
import { Toaster } from 'sonner'

import { router } from './routes'
import { ThemeProvider } from '@/presentation'
import { QueryProvider } from '@/presentation'

import './index.css'

/**
 * Waygrid Admin Panel
 *
 * Architecture: Layered DDD
 * - Domain: Pure business logic
 * - Application: Use cases and orchestration
 * - Infrastructure: External adapters (API, storage)
 * - Presentation: React UI
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryProvider>
      <ThemeProvider defaultTheme="system" storageKey="waygrid-theme">
        <RouterProvider router={router} />
        <Toaster richColors position="top-right" />
      </ThemeProvider>
    </QueryProvider>
  </React.StrictMode>
)

import { Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { CustomersPage } from './pages/CustomersPage'
import { CustomerDetailPage } from './pages/CustomerDetailPage'
import { DashboardPage } from './pages/DashboardPage'
import { MaterialsPage } from './pages/MaterialsPage'
import { NewQuotePage } from './pages/NewQuotePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PrintersPage } from './pages/PrintersPage'
import { QuoteDetailPage } from './pages/QuoteDetailPage'
import { QuoteEditPage } from './pages/QuoteEditPage'
import { QuotesPage } from './pages/QuotesPage'
import { ProductionOrderPage } from './pages/ProductionOrderPage'
import { ProductionPage } from './pages/ProductionPage'
import { LoginPage } from './pages/LoginPage'
import { ProtectedRoute } from './components/ProtectedRoute'

export function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="customers/:id" element={<CustomerDetailPage />} />
          <Route path="materials" element={<MaterialsPage />} />
          <Route path="printers" element={<PrintersPage />} />
          <Route path="quotes" element={<QuotesPage />} />
          <Route path="quotes/new" element={<NewQuotePage />} />
          <Route path="quotes/:id" element={<QuoteDetailPage />} />
          <Route path="quotes/:id/edit" element={<QuoteEditPage />} />
          <Route path="production" element={<ProductionPage />} />
          <Route path="production/:id" element={<ProductionOrderPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

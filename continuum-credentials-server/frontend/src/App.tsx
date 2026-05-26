import { Routes, Route } from 'react-router-dom';
import { CredentialListPage } from './pages';

export default function App() {
  return (
    <Routes>
      <Route path="/*" element={<CredentialListPage />} />
    </Routes>
  );
}

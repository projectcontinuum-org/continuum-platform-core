import { Routes, Route } from 'react-router-dom';
import { WorkbenchListPage } from './pages';

export default function App() {
  return (
    <Routes>
      <Route path="/*" element={<WorkbenchListPage />} />
    </Routes>
  );
}


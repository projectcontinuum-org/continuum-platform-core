import { Routes, Route, Navigate } from 'react-router-dom';
import { WorkbenchListPage, FeatureWorkerListPage } from './pages';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/workbench-manager" replace />} />
      <Route path="/workbench-manager/*" element={<WorkbenchListPage />} />
      <Route path="/feature-manager/*" element={<FeatureWorkerListPage />} />
    </Routes>
  );
}


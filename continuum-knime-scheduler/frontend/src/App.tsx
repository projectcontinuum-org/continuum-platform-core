import { Routes, Route } from 'react-router-dom';
import { KnimeWorkflowsPage } from './pages';

export default function App() {
  return (
    <Routes>
      <Route path="/*" element={<KnimeWorkflowsPage />} />
    </Routes>
  );
}

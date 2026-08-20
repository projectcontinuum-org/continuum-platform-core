import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { UI_BASE } from './basePath';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter basename={UI_BASE}>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);

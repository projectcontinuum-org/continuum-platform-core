import { useState, useEffect, useCallback } from 'react';
import type { CredentialTypeResponse } from '../types/api';
import { credentialTypesApi } from '../api/credentialTypes';

export function useCredentialTypes() {
  const [types, setTypes] = useState<CredentialTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchTypes = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const data = await credentialTypesApi.listTypes();
      setTypes(data);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTypes();
  }, [fetchTypes]);

  // Deduplicate types by name (take latest version)
  const uniqueTypeNames = [...new Set(types.map(t => t.type))];

  // Get the latest version for a given type name
  const getTypeWithVersion = useCallback((typeName: string, version?: string): CredentialTypeResponse | undefined => {
    const matching = types.filter(t => t.type === typeName);
    if (version) {
      return matching.find(t => t.version === version);
    }
    // Return latest by version sorting
    return matching.sort((a, b) => b.version.localeCompare(a.version, undefined, { numeric: true }))[0];
  }, [types]);

  return {
    types,
    uniqueTypeNames,
    loading,
    error,
    refresh: fetchTypes,
    getTypeWithVersion,
  };
}

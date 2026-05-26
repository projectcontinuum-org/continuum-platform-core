import { useMemo, useState, useEffect, useRef, useCallback } from 'react';
import { JsonForms } from '@jsonforms/react';
import type { UISchemaElement, JsonSchema, JsonFormsCore } from '@jsonforms/core';
import {
  materialRenderers,
  materialCells,
} from '@jsonforms/material-renderers';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { KeyValueEditor } from './KeyValueEditor';

interface DynamicFieldRendererProps {
  type: string;
  schema: Record<string, unknown>;
  uiSchema: Record<string, unknown>;
  data: Record<string, string>;
  onChange: (data: Record<string, string>) => void;
  isDark: boolean;
}

/**
 * Determine if this type/schema combination should fall back to the
 * KeyValueEditor instead of JSON Forms.
 */
function shouldUseKeyValueEditor(type: string, schema: Record<string, unknown>): boolean {
  if (type.toLowerCase() === 'generic') return true;
  if (schema.additionalProperties) return true;
  if (schema.type !== 'object') return true;

  const props = schema.properties as Record<string, Record<string, unknown>> | undefined;
  if (!props || Object.keys(props).length === 0) return true;

  const keys = Object.keys(props);
  if (keys.length === 1) {
    const single = props[keys[0]];
    if (single?.type === 'object' && single?.additionalProperties) return true;
  }

  return !Object.values(props).some((prop) => {
    const t = prop.type;
    return t === 'string' || t === 'number' || t === 'integer' || t === 'boolean';
  });
}

function isJsonFormsUiSchema(uiSchema: Record<string, unknown>): boolean {
  return typeof uiSchema.type === 'string' && uiSchema.type !== '';
}

const EMPTY_SCHEMA: Record<string, unknown> = {};
const EMPTY_UI_SCHEMA: Record<string, unknown> = {};

/**
 * Cleans JSON Forms data: keeps only defined, non-null values as strings.
 */
function cleanFormData(raw: Record<string, unknown>): Record<string, string> {
  const cleaned: Record<string, string> = {};
  for (const [key, value] of Object.entries(raw)) {
    if (value !== undefined && value !== null) {
      cleaned[key] = String(value);
    }
  }
  return cleaned;
}

export function DynamicFieldRenderer({
  type,
  schema = EMPTY_SCHEMA,
  uiSchema = EMPTY_UI_SCHEMA,
  data,
  onChange,
  isDark,
}: DynamicFieldRendererProps) {
  // JSON Forms manages its own internal data state to avoid
  // parent re-renders resetting the form / losing cursor position.
  const [internalData, setInternalData] = useState<Record<string, unknown>>(data);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // Sync from parent ONLY when the type or schema changes (not on every data update).
  // This handles: initial load, type dropdown change, edit modal opening with new credential.
  const schemaKey = JSON.stringify(schema);
  useEffect(() => {
    setInternalData(data);
  }, [type, schemaKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleChange = useCallback((state: Pick<JsonFormsCore, 'data' | 'errors'>) => {
    if (state.data) {
      setInternalData(state.data as Record<string, unknown>);
      onChangeRef.current(cleanFormData(state.data as Record<string, unknown>));
    }
  }, []);

  const muiTheme = useMemo(() => createTheme({
    palette: {
      mode: isDark ? 'dark' : 'light',
      primary: {
        main: isDark ? '#C4A8FF' : '#705697',
      },
      background: {
        default: isDark ? '#292D3E' : '#F5F5F5',
        paper: isDark ? '#363C50' : '#FFFFFF',
      },
      text: {
        primary: isDark ? '#EEE7E7' : '#33384D',
        secondary: isDark ? '#A598B8' : '#6C6C6C',
      },
    },
    typography: {
      fontFamily: 'Inter, system-ui, sans-serif',
      fontSize: 14,
    },
    components: {
      MuiOutlinedInput: {
        styleOverrides: {
          root: { borderRadius: 8 },
        },
      },
      MuiFormLabel: {
        styleOverrides: {
          root: { fontSize: '0.875rem' },
        },
      },
    },
  }), [isDark]);

  if (shouldUseKeyValueEditor(type, schema)) {
    return <KeyValueEditor data={data} onChange={onChange} />;
  }

  const validUiSchema = isJsonFormsUiSchema(uiSchema)
    ? (uiSchema as unknown as UISchemaElement)
    : undefined;

  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline enableColorScheme />
      <div className="json-forms-container">
        <JsonForms
          schema={schema as JsonSchema}
          uischema={validUiSchema}
          data={internalData}
          renderers={materialRenderers}
          cells={materialCells}
          onChange={handleChange}
        />
      </div>
    </ThemeProvider>
  );
}

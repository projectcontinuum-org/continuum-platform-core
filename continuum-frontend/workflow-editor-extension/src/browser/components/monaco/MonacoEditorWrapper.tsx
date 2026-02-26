import React, { useEffect, useRef } from 'react';
import { useMUIThemeStore } from '../../store/MUIThemeStore';

export interface MonacoEditorWrapperProps {
    value: string;
    language: string;
    theme?: string;
    height?: string;
    options?: any;
    onChange?: (value: string) => void;
}

export const MonacoEditorWrapper: React.FC<MonacoEditorWrapperProps> = ({
    value,
    language,
    theme,
    height,
    options,
    onChange
}) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const editorRef = useRef<any | null>(null);

    // Get Theia's Monaco instance from the store
    const monaco = useMUIThemeStore((state) => state.monaco);

    // Create editor on mount
    useEffect(() => {
        if (!containerRef.current || !monaco) return;

        const editor = monaco.editor.create(containerRef.current, {
            value: value,
            language: language,
            theme: theme || 'vs-dark',
            'semanticHighlighting.enabled': false,
            ...options
        });

        editorRef.current = editor;

        // Listen to content changes
        const disposable = editor.onDidChangeModelContent(() => {
            onChange?.(editor.getValue());
        });

        // Cleanup on unmount
        return () => {
            disposable.dispose();
            editor.dispose();
            editorRef.current = null;
        };
    }, [monaco]);

    // Sync external value changes
    useEffect(() => {
        if (!editorRef.current) return;

        const currentValue = editorRef.current.getValue();
        if (currentValue !== value) {
            editorRef.current.setValue(value);
        }
    }, [value]);

    // Update theme when changed
    useEffect(() => {
        if (!monaco || !theme || !editorRef.current) return;

        monaco.editor.setTheme(theme);
        editorRef.current.updateOptions({ theme });
    }, [monaco, theme]);

    // Update options when changed
    useEffect(() => {
        if (!editorRef.current || !options) return;
        editorRef.current.updateOptions(options);
    }, [options]);

    return (
        <div
            ref={containerRef}
            style={{
                height: height || '400px',
                width: '100%'
            }}
        />
    );
};

import { createContext, useContext, type ReactNode } from 'react';
import { useColorScheme } from 'react-native';

import { useAuth } from '@/features/auth/AuthContext';
import { useUserPreferences } from '@/features/settings/api';
import { Colors } from '@/constants/theme';

type ResolvedScheme = 'light' | 'dark';

interface ThemeContextValue {
  scheme: ResolvedScheme;
  colors: (typeof Colors)[ResolvedScheme];
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Resolves the real System/Light/Dark account preference (Settings' Appearance
 * picker, ADR-102) against the OS scheme — SYSTEM follows the device, LIGHT/DARK
 * force it regardless of device setting. Previously `useTheme()` was hardcoded to
 * always return Colors.dark ("provisional pending real Phase 28 work," ADR-101);
 * this is that follow-through. Falls back to OS scheme (or dark if the OS reports
 * none) before login/onboarding, where there's no saved preference to read yet.
 */
export function AppThemeProvider({ children }: { children: ReactNode }) {
  const osScheme = useColorScheme();
  const { user } = useAuth();
  const preferencesQuery = useUserPreferences(!!user);
  const preference = preferencesQuery.data?.appearance ?? 'SYSTEM';
  const scheme: ResolvedScheme = preference === 'LIGHT' ? 'light' : preference === 'DARK' ? 'dark' : osScheme === 'light' ? 'light' : 'dark';

  return <ThemeContext.Provider value={{ scheme, colors: Colors[scheme] }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within AppThemeProvider.');
  return context.colors;
}

/** The resolved 'light' | 'dark' scheme itself — used at the navigation-shell level to pick React Navigation's DefaultTheme vs DarkTheme. */
export function useThemeScheme(): ResolvedScheme {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useThemeScheme must be used within AppThemeProvider.');
  return context.scheme;
}

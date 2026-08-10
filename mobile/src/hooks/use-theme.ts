/**
 * Learn more about light and dark modes:
 * https://docs.expo.dev/guides/color-schemes/
 *
 * Provisionally forced to the Rally26 dark-navy brand theme, matching
 * docs/design/mobile_sample_design.png (ADR-101) — the reference design shows a
 * consistent brand-forward dark UI, not "whatever the OS is set to." Real System/
 * Light/Dark alignment with Phase 28's account appearance preference is a Phase 33
 * §32.1 design-system task, not decided by this scaffold.
 */
import { Colors } from '@/constants/theme';

export function useTheme() {
  return Colors.dark;
}

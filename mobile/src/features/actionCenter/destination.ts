import { webEmbedRoute } from '@/lib/webEmbed';

import type { ActionCenterItem } from './types';

/**
 * Maps an action-center item to a real mobile destination — checked directly against
 * backend/.../actioncenter/application/ActionCenterService.kt for what `contextType`/
 * `contextId` actually is per type (it is NOT a uniform "entity this item is about" id;
 * e.g. FEE_PAYMENT's contextId is a *householdId*, not a fee-assignment id, and
 * EVENT_RSVP's is a *participantId*, not an event id — neither matches what
 * fee-details.tsx/event-details.tsx expect as their `id` param). Only types with a
 * confirmed, safe native destination route natively; everything else falls back to the
 * same WebView embed (ADR-106) already used for Swag Shop/Fundraising/Sponsorships,
 * using the item's own `actionPath` — never a guessed native route that could land on
 * the wrong entity.
 */
export function actionCenterDestination(item: ActionCenterItem) {
  switch (item.type) {
    case 'DOCUMENT_ACKNOWLEDGMENT':
      return '/documents' as const;
    case 'SUPPORT_CASE_RESPONSE':
      return '/support-request' as const;
    default:
      return webEmbedRoute(item.actionPath, item.title);
  }
}

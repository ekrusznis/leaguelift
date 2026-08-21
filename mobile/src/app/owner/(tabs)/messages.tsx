import { router } from 'expo-router';

import { MessagesListScreen } from '@/features/messaging/MessagesListScreen';

/**
 * Owner's own inbox (threads they're a direct participant/recipient of), plus a clear
 * link into the org-wide oversight view (`/owner/broadcasts-manage`, which despite its
 * route name already lists every thread type in the org — see that screen's own
 * comment) — Owner needs both "threads I'm part of" and "everything happening in my
 * org," not just the latter.
 */
export default function OwnerMessagesScreen() {
  return (
    <MessagesListScreen
      oversightLink={{ label: 'View all organization messages', onPress: () => router.push('/owner/broadcasts-manage') }}
    />
  );
}

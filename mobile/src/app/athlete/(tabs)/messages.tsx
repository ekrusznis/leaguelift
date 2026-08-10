import { router } from 'expo-router';

import { MessagesListScreen } from '@/features/messaging/MessagesListScreen';

export default function AthleteMessagesScreen() {
  return <MessagesListScreen onNewConversation={() => router.push('/athlete/new-conversation')} />;
}

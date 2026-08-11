import { router } from 'expo-router';

import { MessagesListScreen } from '@/features/messaging/MessagesListScreen';
import { useCoach } from '@/features/teams/CoachContext';

export default function CoachMessagesScreen() {
  const { selectedTeamId } = useCoach();
  return (
    <MessagesListScreen
      onNewConversation={selectedTeamId ? () => router.push({ pathname: '/message-compose', params: { teamId: selectedTeamId } }) : undefined}
    />
  );
}

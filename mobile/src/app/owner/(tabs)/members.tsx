import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { ConfirmDialog } from '@/components/confirm-dialog';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import { useMembers, useRevokeMember, useUpdateMemberRole } from '@/features/membership/api';
import type { MembershipResponse, MembershipRole } from '@/features/membership/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const ASSIGNABLE_ROLES: MembershipRole[] = ['ADMINISTRATOR', 'VIEWER', 'TEAM_ADMINISTRATOR', 'TOURNAMENT_ADMINISTRATOR'];

/**
 * Members list + role update/revoke (Members tab, ADR-105). Mutating actions aren't
 * hidden client-side for non-manager owners (VIEWER-tier) — a 403 from the backend
 * surfaces as a clear toast instead, matching how permission errors are already
 * handled elsewhere in the app (RSVP, messaging) rather than duplicating capability
 * checks client-side for a first slice.
 */
export default function OwnerMembersScreen() {
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const membersQuery = useMembers(organizationId);
  const updateRole = useUpdateMemberRole(organizationId);
  const revokeMember = useRevokeMember(organizationId);

  const [roleTarget, setRoleTarget] = useState<MembershipResponse | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<MembershipResponse | null>(null);

  function changeRole(role: MembershipRole) {
    if (!roleTarget) return;
    updateRole.mutate(
      { memberId: roleTarget.id, role },
      {
        onSuccess: () => toast.show('Member role updated.', 'success'),
        onError: () => toast.show("Could not update that member's role.", 'error'),
      },
    );
    setRoleTarget(null);
  }

  function confirmRevoke() {
    if (!revokeTarget) return;
    revokeMember.mutate(revokeTarget.id, {
      onSuccess: () => toast.show('Member removed.', 'success'),
      onError: () => toast.show('Could not remove that member.', 'error'),
    });
    setRevokeTarget(null);
  }

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Members</ThemedText>
      </View>

      {membersQuery.isLoading && <LoadingState label="Loading members…" />}
      {membersQuery.isError && <ErrorState message="Could not load members." onRetry={() => membersQuery.refetch()} />}
      {membersQuery.data && membersQuery.data.items.length === 0 && (
        <EmptyState title="No members yet" description="Staff invited to this organization will show up here." />
      )}

      <ScrollView contentContainerStyle={styles.list}>
        {membersQuery.data?.items.map((member) => (
          <ThemedView key={member.id} type="backgroundElement" style={styles.row}>
            <View style={styles.rowBody}>
              <ThemedText type="smallBold">{member.userDisplayName ?? member.userEmail ?? 'Unknown'}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {member.role} · {member.status}
              </ThemedText>
            </View>
            {member.role !== 'OWNER' && (
              <View style={styles.rowActions}>
                <Pressable hitSlop={8} onPress={() => setRoleTarget(member)}>
                  <Ionicons name="create-outline" size={20} color={theme.textSecondary} />
                </Pressable>
                <Pressable hitSlop={8} onPress={() => setRevokeTarget(member)}>
                  <Ionicons name="trash-outline" size={20} color={Brand.errorRed} />
                </Pressable>
              </View>
            )}
          </ThemedView>
        ))}
      </ScrollView>

      <Modal visible={!!roleTarget} onClose={() => setRoleTarget(null)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>
          Change role for {roleTarget?.userDisplayName ?? roleTarget?.userEmail}
        </ThemedText>
        {ASSIGNABLE_ROLES.map((role) => (
          <Pressable key={role} onPress={() => changeRole(role)} style={styles.roleOption}>
            <ThemedText type={role === roleTarget?.role ? 'smallBold' : 'default'}>{role}</ThemedText>
            {role === roleTarget?.role && <Ionicons name="checkmark" size={18} color={Brand.championshipGold} />}
          </Pressable>
        ))}
      </Modal>

      <ConfirmDialog
        visible={!!revokeTarget}
        title="Remove member?"
        message={`${revokeTarget?.userDisplayName ?? revokeTarget?.userEmail ?? 'This member'} will lose access to this organization.`}
        confirmLabel="Remove"
        destructive
        onConfirm={confirmRevoke}
        onCancel={() => setRevokeTarget(null)}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  rowBody: {
    flex: 1,
    gap: 2,
  },
  rowActions: {
    flexDirection: 'row',
    gap: Spacing.three,
  },
  modalTitle: {
    marginBottom: Spacing.three,
  },
  roleOption: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.three,
  },
});

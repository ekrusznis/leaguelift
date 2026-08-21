import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { ConfirmDialog } from '@/components/confirm-dialog';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { ListFooter } from '@/components/list-footer';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import { useDashboardContext } from '@/features/dashboard/api';
import {
  useInviteOwnershipTransfer,
  usePendingOwnershipTransferInvitation,
  useRevokeMember,
  useRevokeOwnershipTransferInvitation,
  useTransferOwnership,
  useUpdateMemberRole,
} from '@/features/membership/api';
import type { MembershipResponse, MembershipRole } from '@/features/membership/types';
import { flattenInfiniteItems, useInfiniteMemberSearch, type MemberSearchSort } from '@/features/people-search/api';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const ASSIGNABLE_ROLES: MembershipRole[] = ['ADMINISTRATOR', 'VIEWER', 'TEAM_ADMINISTRATOR', 'TOURNAMENT_ADMINISTRATOR'];

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Owner',
  ADMINISTRATOR: 'Administrator',
  VIEWER: 'Viewer',
  TEAM_ADMINISTRATOR: 'Team administrator',
  TOURNAMENT_ADMINISTRATOR: 'Tournament administrator',
};

export default function OwnerMembersScreen() {
  const theme = useTheme();
  const toast = useToast();
  const { user } = useAuth();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const updateRole = useUpdateMemberRole(organizationId);
  const revokeMember = useRevokeMember(organizationId);
  const transferOwnership = useTransferOwnership(organizationId);

  const [query, setQuery] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [sort, setSort] = useState<MemberSearchSort>('NAME_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [roleTarget, setRoleTarget] = useState<MembershipResponse | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<MembershipResponse | null>(null);
  const [transferTarget, setTransferTarget] = useState<MembershipResponse | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');

  const membersQuery = useInfiniteMemberSearch(organizationId, { q: query, role, status, sort });
  const members = useMemo(() => flattenInfiniteItems(membersQuery.data?.pages), [membersQuery.data?.pages]);
  const total = membersQuery.data?.pages[0]?.totalElements ?? 0;
  const isViewerOwner = members.some((member) => member.role === 'OWNER' && !!user?.id && member.userId === user.id);
  const pendingOwnershipInvitation = usePendingOwnershipTransferInvitation(organizationId, isViewerOwner);
  const inviteOwnershipTransfer = useInviteOwnershipTransfer(organizationId);
  const revokeOwnershipInvitation = useRevokeOwnershipTransferInvitation(organizationId);
  const activeFilters = [
    ...(role ? [ROLE_LABELS[role] ?? role] : []),
    ...(status && status !== 'ACTIVE' ? [status === 'REVOKED' ? 'Disabled' : status] : []),
  ];
  const sortLabel =
    sort === 'NAME_ASC' ? 'Name A–Z'
    : sort === 'NAME_DESC' ? 'Name Z–A'
    : sort === 'ROLE_ASC' ? 'Role'
    : sort === 'NEWEST' ? 'Newest'
    : 'Oldest';

  function changeRole(nextRole: MembershipRole) {
    if (!roleTarget) return;
    updateRole.mutate(
      { memberId: roleTarget.id, role: nextRole },
      {
        onSuccess: () => toast.show('Member role updated.', 'success'),
        onError: () => toast.show("Could not update that member's role.", 'error'),
      },
    );
    setRoleTarget(null);
  }

  function confirmDisable() {
    if (!revokeTarget) return;
    revokeMember.mutate(revokeTarget.id, {
      onSuccess: () => toast.show('Member access disabled.', 'success'),
      onError: () => toast.show("Could not disable that member's access.", 'error'),
    });
    setRevokeTarget(null);
  }

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Members & Staff</ThemedText>
        {isViewerOwner && (
          <Pressable accessibilityRole="button" accessibilityLabel="Transfer ownership" hitSlop={8} onPress={() => setInviteOpen(true)}>
            <ThemedText type="small" style={{ color: Brand.championshipGold }}>Transfer ownership</ThemedText>
          </Pressable>
        )}
      </View>

      {membersQuery.isLoading && <LoadingState label="Loading members…" />}
      {membersQuery.isError && <ErrorState message="Could not load members." onRetry={() => membersQuery.refetch()} />}

      {!membersQuery.isLoading && !membersQuery.isError && (
        <FlatList
          data={members}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.controls}>
              <ListControls
                query={query}
                onChangeQuery={setQuery}
                searchPlaceholder="Search members by name or email"
                resultCount={total}
                activeFilters={activeFilters}
                onRemoveFilter={(index) => {
                  if (role && index === 0) setRole('');
                  else setStatus('ACTIVE');
                }}
                onClearFilters={() => {
                  setQuery('');
                  setRole('');
                  setStatus('ACTIVE');
                  setSort('NAME_ASC');
                }}
                onPressFilter={() => setFilterOpen(true)}
                onPressSort={() => setSortOpen(true)}
                sortLabel={sortLabel}
              />
            </View>
          }
          ListEmptyComponent={
            <EmptyState
              title={query.trim() || role || status !== 'ACTIVE' ? 'No results found' : 'No active members yet'}
              description={
                query.trim() || role || status !== 'ACTIVE'
                  ? 'Try changing your search or filters.'
                  : 'Accepted organization members will appear here.'
              }
            />
          }
          ListFooterComponent={
            <ListFooter
              loadedCount={members.length}
              totalCount={total}
              hasMore={!!membersQuery.hasNextPage}
              loadingMore={membersQuery.isFetchingNextPage}
              onLoadMore={() => membersQuery.fetchNextPage()}
            />
          }
          renderItem={({ item }) => (
            <ThemedView type="backgroundElement" style={styles.row}>
              <View style={styles.rowBody}>
                <ThemedText type="smallBold">{item.userDisplayName ?? item.userEmail ?? 'Organization member'}</ThemedText>
                {item.userEmail ? (
                  <ThemedText type="small" themeColor="textSecondary">{item.userEmail}</ThemedText>
                ) : null}
                <ThemedText type="small" themeColor="textSecondary">
                  {ROLE_LABELS[item.role] ?? item.role} · {item.status === 'REVOKED' ? 'Disabled' : item.status}
                </ThemedText>
              </View>
              {item.role !== 'OWNER' && item.status === 'ACTIVE' && (
                <View style={styles.rowActions}>
                  {isViewerOwner && item.role === 'ADMINISTRATOR' && (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`Make ${item.userDisplayName ?? item.userEmail ?? 'member'} the owner`}
                      hitSlop={8}
                      onPress={() => setTransferTarget(item)}>
                      <Ionicons name="ribbon-outline" size={20} color={Brand.championshipGold} />
                    </Pressable>
                  )}
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Change role for ${item.userDisplayName ?? item.userEmail ?? 'member'}`}
                    hitSlop={8}
                    onPress={() => setRoleTarget(item)}>
                    <Ionicons name="create-outline" size={20} color={theme.textSecondary} />
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Disable access for ${item.userDisplayName ?? item.userEmail ?? 'member'}`}
                    hitSlop={8}
                    onPress={() => setRevokeTarget(item)}>
                    <Ionicons name="person-remove-outline" size={20} color={Brand.errorRed} />
                  </Pressable>
                </View>
              )}
            </ThemedView>
          )}
        />
      )}

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter members</ThemedText>
        <ThemedText type="smallBold" style={styles.filterHeading}>Status</ThemedText>
        {([
          ['', 'All statuses'],
          ['ACTIVE', 'Active'],
          ['REVOKED', 'Disabled'],
          ['INVITED', 'Invited'],
        ] as const).map(([value, label]) => (
          <Pressable key={value || 'ALL_STATUS'} onPress={() => setStatus(value)} style={styles.option}>
            <ThemedText type={status === value ? 'smallBold' : 'default'}>{label}</ThemedText>
            {status === value && <Ionicons name="checkmark" size={18} color={theme.text} />}
          </Pressable>
        ))}
        <ThemedText type="smallBold" style={styles.filterHeading}>Role</ThemedText>
        {[['', 'All roles'], ...Object.entries(ROLE_LABELS)].map(([value, label]) => (
          <Pressable key={value || 'ALL_ROLES'} onPress={() => setRole(value)} style={styles.option}>
            <ThemedText type={role === value ? 'smallBold' : 'default'}>{label}</ThemedText>
            {role === value && <Ionicons name="checkmark" size={18} color={theme.text} />}
          </Pressable>
        ))}
        <Pressable onPress={() => setFilterOpen(false)} style={styles.doneButton}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort members</ThemedText>
        {([
          ['NAME_ASC', 'Name A–Z'],
          ['NAME_DESC', 'Name Z–A'],
          ['ROLE_ASC', 'Role'],
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
        ] as const).map(([value, label]) => (
          <Pressable
            key={value}
            onPress={() => {
              setSort(value);
              setSortOpen(false);
            }}
            style={styles.option}>
            <ThemedText type={sort === value ? 'smallBold' : 'default'}>{label}</ThemedText>
            {sort === value && <Ionicons name="checkmark" size={18} color={theme.text} />}
          </Pressable>
        ))}
      </Modal>

      <Modal visible={!!roleTarget} onClose={() => setRoleTarget(null)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>
          Change role for {roleTarget?.userDisplayName ?? roleTarget?.userEmail}
        </ThemedText>
        {ASSIGNABLE_ROLES.map((nextRole) => (
          <Pressable key={nextRole} onPress={() => changeRole(nextRole)} style={styles.option}>
            <ThemedText type={nextRole === roleTarget?.role ? 'smallBold' : 'default'}>
              {ROLE_LABELS[nextRole] ?? nextRole}
            </ThemedText>
            {nextRole === roleTarget?.role && <Ionicons name="checkmark" size={18} color={Brand.championshipGold} />}
          </Pressable>
        ))}
      </Modal>

      <ConfirmDialog
        visible={!!revokeTarget}
        title="Disable access?"
        message={`${revokeTarget?.userDisplayName ?? revokeTarget?.userEmail ?? 'This member'} will no longer be able to access this organization. Historical activity and audit records will remain.`}
        confirmLabel="Disable access"
        destructive
        onConfirm={confirmDisable}
        onCancel={() => setRevokeTarget(null)}
      />

      <ConfirmDialog
        visible={!!transferTarget}
        title="Transfer ownership?"
        message={`${transferTarget?.userDisplayName ?? transferTarget?.userEmail ?? 'This member'} will become the organization owner. You will become an Administrator.`}
        confirmLabel="Transfer ownership"
        destructive
        onConfirm={() => {
          if (!transferTarget) return;
          transferOwnership.mutate(transferTarget.id, {
            onSuccess: () => toast.show('Ownership transferred.', 'success'),
            onError: () => toast.show('Could not transfer ownership.', 'error'),
          });
          setTransferTarget(null);
        }}
        onCancel={() => setTransferTarget(null)}
      />

      <Modal visible={inviteOpen} onClose={() => setInviteOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Transfer ownership</ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.filterHeading}>
          Hand this organization to an existing Administrator using the ribbon icon on their row, or invite someone new by
          email below. You&rsquo;ll become an Administrator once the transfer completes.
        </ThemedText>
        {pendingOwnershipInvitation.data ? (
          <View style={styles.pendingInviteRow}>
            <ThemedText type="small">Invitation sent to {pendingOwnershipInvitation.data.email} — pending.</ThemedText>
            <Pressable
              accessibilityRole="button"
              onPress={() => {
                revokeOwnershipInvitation.mutate(pendingOwnershipInvitation.data!.id, {
                  onSuccess: () => toast.show('Invitation revoked.', 'success'),
                  onError: () => toast.show('Could not revoke the invitation.', 'error'),
                });
              }}
              style={styles.doneButton}>
              <ThemedText type="smallBold" style={{ color: Brand.errorRed }}>Revoke</ThemedText>
            </Pressable>
          </View>
        ) : (
          <>
            <TextInput
              value={inviteEmail}
              onChangeText={setInviteEmail}
              placeholder="Email address"
              placeholderTextColor={theme.textSecondary}
              autoCapitalize="none"
              keyboardType="email-address"
              style={[styles.input, { color: theme.text, borderColor: theme.textSecondary }]}
            />
            <Pressable
              accessibilityRole="button"
              onPress={() => {
                inviteOwnershipTransfer.mutate(inviteEmail.trim(), {
                  onSuccess: () => {
                    toast.show('Invitation sent.', 'success');
                    setInviteEmail('');
                    setInviteOpen(false);
                  },
                  onError: () => toast.show('Could not send the invitation.', 'error'),
                });
              }}
              style={styles.doneButton}>
              <ThemedText type="smallBold">{inviteOwnershipTransfer.isPending ? 'Sending…' : 'Send invitation'}</ThemedText>
            </Pressable>
          </>
        )}
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  pendingInviteRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.three,
    marginTop: Spacing.two,
  },
  input: {
    minHeight: 46,
    borderWidth: 1,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    marginTop: Spacing.two,
  },
  controls: { paddingBottom: Spacing.three },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    marginBottom: Spacing.two,
  },
  rowBody: { flex: 1, gap: 2 },
  rowActions: { flexDirection: 'row', gap: Spacing.three },
  modalTitle: { marginBottom: Spacing.three },
  filterHeading: { marginTop: Spacing.two, marginBottom: Spacing.one },
  option: {
    minHeight: 46,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  doneButton: {
    minHeight: 46,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.three,
  },
});

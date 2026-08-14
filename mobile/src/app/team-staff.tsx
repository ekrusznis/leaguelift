import Ionicons from '@expo/vector-icons/Ionicons';
import { useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Modal } from '@/components/modal';
import { useTeamStaff } from '@/features/team-staff/api';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type StaffSort = 'NAME_ASC' | 'NAME_DESC' | 'ROLE_ASC';

export default function TeamStaffScreen() {
  const { organizationId, teamId, teamName } = useLocalSearchParams<{
    organizationId: string;
    teamId: string;
    teamName?: string;
  }>();
  const theme = useTheme();
  const staffQuery = useTeamStaff(organizationId ?? null, teamId ?? null);
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<StaffSort>('NAME_ASC');
  const [sortOpen, setSortOpen] = useState(false);

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const filtered = (staffQuery.data ?? []).filter((staff) =>
      !needle || `${staff.displayName} ${staff.roleLabel}`.toLowerCase().includes(needle),
    );
    return [...filtered].sort((a, b) => {
      if (sort === 'ROLE_ASC') {
        return a.roleLabel.localeCompare(b.roleLabel) || a.displayName.localeCompare(b.displayName);
      }
      const value = a.displayName.localeCompare(b.displayName);
      return sort === 'NAME_ASC' ? value : -value;
    });
  }, [query, sort, staffQuery.data]);

  const sortLabel = sort === 'NAME_ASC' ? 'Name A–Z' : sort === 'NAME_DESC' ? 'Name Z–A' : 'Role';

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Coaches & Staff" />
      <View style={styles.context}>
        {teamName ? <ThemedText type="smallBold">{teamName}</ThemedText> : null}
        <ThemedText type="small" themeColor="textSecondary">
          Team-assigned staff. Private email and phone information is not shown here.
        </ThemedText>
      </View>

      {staffQuery.isLoading && <LoadingState label="Loading team staff…" />}
      {staffQuery.isError && (
        <ErrorState message="Could not load this team's staff." onRetry={() => staffQuery.refetch()} />
      )}

      {staffQuery.data && (
        <FlatList
          data={visible}
          keyExtractor={(item) => item.userId}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.controls}>
              <ListControls
                query={query}
                onChangeQuery={setQuery}
                searchPlaceholder="Search coaches and staff"
                resultCount={visible.length}
                onPressSort={() => setSortOpen(true)}
                sortLabel={sortLabel}
                onClearFilters={() => {
                  setQuery('');
                  setSort('NAME_ASC');
                }}
              />
            </View>
          }
          ListEmptyComponent={
            <EmptyState
              title={query.trim() ? 'No results found' : 'No coaches or team staff yet'}
              description={
                query.trim()
                  ? 'Try changing your search.'
                  : 'Assigned coaches and team staff will appear here.'
              }
            />
          }
          renderItem={({ item }) => (
            <ThemedView type="backgroundElement" style={styles.row}>
              <View style={[styles.avatar, { backgroundColor: theme.backgroundSelected }]}>
                <Ionicons name="person-outline" size={20} color={theme.text} />
              </View>
              <View style={styles.rowBody}>
                <ThemedText type="smallBold">{item.displayName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {item.roleLabel}
                </ThemedText>
              </View>
            </ThemedView>
          )}
        />
      )}

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort coaches & staff</ThemedText>
        {([
          ['NAME_ASC', 'Name A–Z'],
          ['NAME_DESC', 'Name Z–A'],
          ['ROLE_ASC', 'Role'],
        ] as const).map(([value, label]) => (
          <ThemedView key={value}>
            <ThemedText
              type={sort === value ? 'smallBold' : 'default'}
              onPress={() => {
                setSort(value);
                setSortOpen(false);
              }}>
              {label}
            </ThemedText>
            <View style={styles.optionSpacer} />
          </ThemedView>
        ))}
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  context: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
    gap: Spacing.one,
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
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowBody: { flex: 1, gap: 2 },
  modalTitle: { marginBottom: Spacing.three },
  optionSpacer: { height: Spacing.three },
});

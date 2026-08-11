import Ionicons from '@expo/vector-icons/Ionicons';
import { StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import { useDashboardContext } from '@/features/dashboard/api';
import { useAcknowledgeDocument, useDocumentAcknowledgments, useHouseholdDocuments } from '@/features/documents/api';
import type { DocumentResponse } from '@/features/documents/types';
import { Brand, Spacing } from '@/constants/theme';
import { Button } from '@/components/button';

/**
 * Real household document list + acknowledge (ADR-103) — a plain upload/acknowledge
 * flow for organization/household documents, separate from Phase 31 eligibility/waiver
 * requirements (see app/eligibility.tsx), which have their own status/evidence model.
 */
export default function DocumentsScreen() {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const householdId = dashboardContext.data?.householdId ?? null;
  const documentsQuery = useHouseholdDocuments(organizationId, householdId);
  const acknowledge = useAcknowledgeDocument(organizationId, householdId);
  const toast = useToast();

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Documents" />
      {documentsQuery.isLoading && <LoadingState label="Loading documents…" />}
      {documentsQuery.isError && <ErrorState message="Could not load documents." onRetry={() => documentsQuery.refetch()} />}
      {documentsQuery.data && documentsQuery.data.items.length === 0 && (
        <EmptyState title="No documents" description="No documents have been shared with your household yet." />
      )}
      <View style={styles.list}>
        {documentsQuery.data?.items.map((doc) => (
          <DocumentRow
            key={doc.id}
            document={doc}
            organizationId={organizationId}
            onAcknowledge={() =>
              acknowledge.mutate(doc.id, {
                onSuccess: () => toast.show('Marked as acknowledged.', 'success'),
                onError: () => toast.show('Could not save that. Please try again.', 'error'),
              })
            }
            acknowledging={acknowledge.isPending}
          />
        ))}
      </View>
    </ThemedView>
  );
}

function DocumentRow({
  document,
  organizationId,
  onAcknowledge,
  acknowledging,
}: {
  document: DocumentResponse;
  organizationId: string | null;
  onAcknowledge: () => void;
  acknowledging: boolean;
}) {
  const { user } = useAuth();
  const acknowledgmentsQuery = useDocumentAcknowledgments(organizationId, document.id);
  const acknowledgedByMe = acknowledgmentsQuery.data?.items.some((a) => a.acknowledgedByUserId === user?.id);

  return (
    <ThemedView type="backgroundElement" style={styles.row}>
      <Ionicons name="document-text" size={22} color={Brand.championshipGold} />
      <View style={styles.body}>
        <ThemedText type="smallBold">{document.title ?? 'Document'}</ThemedText>
        {acknowledgedByMe ? (
          <ThemedText type="small" themeColor="textSecondary">
            Acknowledged
          </ThemedText>
        ) : (
          <Button variant="secondary" onPress={onAcknowledge} disabled={acknowledging}>
            Acknowledge
          </Button>
        )}
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  list: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    alignItems: 'center',
  },
  body: {
    flex: 1,
    gap: Spacing.two,
  },
});

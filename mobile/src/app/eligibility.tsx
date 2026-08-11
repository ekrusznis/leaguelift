import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import { useParticipantEligibilityRequirements, useSubmitGuardianEvidence } from '@/features/eligibility/api';
import type { ParticipantRequirement } from '@/features/eligibility/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { webEmbedRoute } from '@/lib/webEmbed';

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Submitted',
  SUPERSEDED: 'Replaced by a newer submission',
  REVOKED: 'Revoked',
  REJECTED: 'Rejected — please resubmit',
  EXPIRED: 'Expired — please resubmit',
};

/**
 * Guardian/athlete-self eligibility requirements + native e-sign/acknowledge (Phase 31
 * slice 31.4, mirrors frontend/src/features/eligibility/ParticipantEligibilityPanel.tsx).
 * Reached from the Parent dashboard's athlete cards (with householdId, to enable a
 * "Continue on rally26.com" link for FILE_UPLOAD requirements) and from the Athlete
 * persona's More tab (without householdId — an athlete's own account has no household
 * page to redirect to, so that requirement type just explains what's needed instead).
 */
export default function EligibilityScreen() {
  const { participantId, participantName, householdId } = useLocalSearchParams<{
    participantId: string;
    participantName?: string;
    householdId?: string;
  }>();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const requirementsQuery = useParticipantEligibilityRequirements(organizationId, participantId ?? null);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={participantName ? `${participantName}'s Eligibility` : 'Eligibility'} />
      {requirementsQuery.isLoading && <LoadingState label="Loading eligibility requirements…" />}
      {requirementsQuery.isError && (
        <ErrorState message="Could not load eligibility requirements." onRetry={() => requirementsQuery.refetch()} />
      )}
      {requirementsQuery.data && requirementsQuery.data.length === 0 && (
        <EmptyState title="Nothing outstanding" description="No eligibility requirements apply to this athlete's current teams." />
      )}
      <View style={styles.list}>
        {requirementsQuery.data?.map((item) => (
          <RequirementCard
            key={item.requirement.id}
            item={item}
            organizationId={organizationId}
            participantId={participantId ?? null}
            householdId={householdId ?? null}
          />
        ))}
      </View>
    </ThemedView>
  );
}

function RequirementCard({
  item,
  organizationId,
  participantId,
  householdId,
}: {
  item: ParticipantRequirement;
  organizationId: string | null;
  participantId: string | null;
  householdId: string | null;
}) {
  const { requirement, evidence } = item;
  const [actionOpen, setActionOpen] = useState(false);
  const isSatisfied = evidence?.status === 'ACTIVE' && (!evidence.expiresAt || new Date(evidence.expiresAt) > new Date());

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <View style={styles.cardHeader}>
        <ThemedText type="smallBold" style={styles.cardTitle}>
          {requirement.title}
        </ThemedText>
        <ThemedView type={isSatisfied ? 'backgroundSelected' : undefined} style={[styles.badge, !isSatisfied && styles.badgeAction]}>
          <ThemedText type="small" style={isSatisfied ? undefined : styles.badgeActionText}>
            {isSatisfied ? 'Complete' : 'Action needed'}
          </ThemedText>
        </ThemedView>
      </View>
      <ThemedText type="small" themeColor="textSecondary" style={styles.content}>
        {requirement.content}
      </ThemedText>
      {evidence && (
        <ThemedText type="small" themeColor="textSecondary">
          {STATUS_LABELS[evidence.status] ?? evidence.status}
          {evidence.acceptedAt ? ` · ${new Date(evidence.acceptedAt).toLocaleDateString()}` : ''}
          {evidence.expiresAt ? ` · Expires ${new Date(evidence.expiresAt).toLocaleDateString()}` : ''}
        </ThemedText>
      )}

      {!isSatisfied && requirement.mode === 'STAFF_REVIEWED_EXTERNAL' && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.staffNote}>
          This requirement is verified by staff — no action needed here.
        </ThemedText>
      )}

      {!isSatisfied && requirement.mode === 'FILE_UPLOAD' && (
        <View style={styles.staffNote}>
          <ThemedText type="small" themeColor="textSecondary">
            Document upload isn&rsquo;t available in the app yet.
            {householdId ? ' Continue on the Rally26 website to upload it.' : ' Ask a guardian to upload it from the Rally26 website.'}
          </ThemedText>
          {householdId && (
            <Button
              variant="secondary"
              style={styles.uploadButton}
              onPress={() =>
                router.push(
                  webEmbedRoute(
                    `/app/organizations/${organizationId}/households/${householdId}/participants`,
                    'Eligibility Documents',
                  ),
                )
              }>
              Continue on rally26.com
            </Button>
          )}
        </View>
      )}

      {!isSatisfied && requirement.mode !== 'STAFF_REVIEWED_EXTERNAL' && requirement.mode !== 'FILE_UPLOAD' && !actionOpen && (
        <Button variant="secondary" style={styles.actionButton} onPress={() => setActionOpen(true)}>
          {requirement.mode === 'GUARDIAN_LEGAL_NAME_SIGNATURE' ? 'Sign' : 'Acknowledge'}
        </Button>
      )}
      {!isSatisfied && actionOpen && requirement.mode === 'GUARDIAN_LEGAL_NAME_SIGNATURE' && (
        <LegalNameSignForm
          organizationId={organizationId}
          participantId={participantId}
          requirementId={requirement.id}
          onDone={() => setActionOpen(false)}
        />
      )}
      {!isSatisfied && actionOpen && (requirement.mode === 'GUARDIAN_ESIGN_ACKNOWLEDGMENT' || requirement.mode === 'INFORMATIONAL') && (
        <AcknowledgmentForm
          organizationId={organizationId}
          participantId={participantId}
          requirementId={requirement.id}
          onDone={() => setActionOpen(false)}
        />
      )}
    </ThemedView>
  );
}

function AcknowledgmentForm({
  organizationId,
  participantId,
  requirementId,
  onDone,
}: {
  organizationId: string | null;
  participantId: string | null;
  requirementId: string;
  onDone: () => void;
}) {
  const submitEvidence = useSubmitGuardianEvidence(organizationId, participantId);
  const toast = useToast();
  const [submitting, setSubmitting] = useState(false);

  async function onAcknowledge() {
    setSubmitting(true);
    try {
      await submitEvidence.mutateAsync({ requirementId, acceptanceMethod: 'ACKNOWLEDGMENT_CHECKBOX' });
      onDone();
    } catch {
      toast.show('Could not save that. Please try again.', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.inlineForm}>
      <ThemedText type="small" themeColor="textSecondary">
        By tapping below, you acknowledge you have read and agree to this requirement.
      </ThemedText>
      <Button onPress={onAcknowledge} disabled={submitting} style={styles.actionButton}>
        {submitting ? 'Submitting…' : 'I acknowledge'}
      </Button>
    </View>
  );
}

function LegalNameSignForm({
  organizationId,
  participantId,
  requirementId,
  onDone,
}: {
  organizationId: string | null;
  participantId: string | null;
  requirementId: string;
  onDone: () => void;
}) {
  const theme = useTheme();
  const submitEvidence = useSubmitGuardianEvidence(organizationId, participantId);
  const toast = useToast();
  const [legalName, setLegalName] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function onSign() {
    if (!legalName.trim()) {
      toast.show('Type your full legal name to sign.', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await submitEvidence.mutateAsync({ requirementId, acceptanceMethod: 'ESIGN_LEGAL_NAME', enteredLegalName: legalName.trim() });
      setLegalName('');
      onDone();
    } catch {
      toast.show('Could not save that. Please try again.', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.inlineForm}>
      <ThemedText type="small" themeColor="textSecondary">
        Type your full legal name to sign
      </ThemedText>
      <TextInput
        value={legalName}
        onChangeText={setLegalName}
        placeholder="Full legal name"
        placeholderTextColor={theme.textSecondary}
        style={[styles.input, { color: theme.text, backgroundColor: theme.background }]}
      />
      <Button onPress={onSign} disabled={submitting} style={styles.actionButton}>
        {submitting ? 'Signing…' : 'Sign'}
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  cardTitle: {
    flex: 1,
  },
  content: {
    marginBottom: Spacing.one,
  },
  badge: {
    borderRadius: 999,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
  },
  badgeAction: {
    backgroundColor: `${Brand.errorRed}22`,
  },
  badgeActionText: {
    color: Brand.errorRed,
  },
  staffNote: {
    marginTop: Spacing.two,
    gap: Spacing.two,
  },
  actionButton: {
    marginTop: Spacing.two,
    alignSelf: 'flex-start',
  },
  uploadButton: {
    alignSelf: 'flex-start',
  },
  inlineForm: {
    marginTop: Spacing.two,
    gap: Spacing.two,
  },
  input: {
    minHeight: 44,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
  },
});

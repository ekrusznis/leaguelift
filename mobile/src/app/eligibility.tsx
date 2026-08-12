import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, TextInput, View } from 'react-native';

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
import { useConfirmMediaUpload, useRequestMediaUpload } from '@/features/media/api';
import { uploadToSignedUrl, type PickedFile } from '@/features/media/uploadToSignedUrl';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const MAX_DOCUMENT_BYTES = 15 * 1024 * 1024;

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Submitted',
  SUPERSEDED: 'Replaced by a newer submission',
  REVOKED: 'Revoked',
  REJECTED: 'Rejected — please resubmit',
  EXPIRED: 'Expired — please resubmit',
};

/**
 * Guardian/athlete-self eligibility requirements + native e-sign/acknowledge/upload
 * (Phase 31 slice 31.4; native FILE_UPLOAD added Phase 37.9, ADR-118) — mirrors
 * frontend/src/features/eligibility/ParticipantEligibilityPanel.tsx. Reached from the
 * Parent dashboard's athlete cards (with householdId, enabling the native document
 * upload form below) and from the Athlete persona's More tab (without householdId — an
 * athlete's own account has no upload capability here, so FILE_UPLOAD requirements just
 * explain a guardian must do it instead).
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
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ThemedView style={styles.container}>
        <ScreenHeader title={participantName ? `${participantName}'s Eligibility` : 'Eligibility'} />
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
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
        </ScrollView>
      </ThemedView>
    </KeyboardAvoidingView>
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

      {!isSatisfied && requirement.mode === 'FILE_UPLOAD' && householdId && (
        <DocumentUploadForm organizationId={organizationId} participantId={participantId} requirementId={requirement.id} />
      )}
      {!isSatisfied && requirement.mode === 'FILE_UPLOAD' && !householdId && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.staffNote}>
          Ask a guardian to upload this document from their account.
        </ThemedText>
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

const REJECTION_MESSAGES: Record<string, string> = {
  FILE_TOO_LARGE: 'That file is too large (max 15 MB). Try a smaller photo or file.',
  UNRECOGNIZED_FILE_FORMAT: "That file type isn't supported. Use a PDF or a photo (JPEG/PNG).",
  CONTENT_TYPE_MISMATCH: "That file's contents didn't match its file type. Try picking it again.",
  INVALID_IMAGE: "That photo couldn't be read. Try picking it again.",
  IMAGE_DIMENSIONS_TOO_LARGE: "That photo's resolution is too large. Try a smaller photo.",
};

/**
 * Native document/photo upload for a FILE_UPLOAD eligibility requirement (Phase 37.9,
 * ADR-118) — previously mobile had no upload capability at all and pushed guardians to
 * the website. Mirrors frontend/src/features/eligibility/ParticipantEligibilityPanel.tsx's
 * DocumentEvidenceForm's exact request-upload -> PUT -> confirm -> submit-evidence
 * sequence. A guardian is far more likely to photograph a paper form than to already
 * have a PDF saved on their phone, so both a camera/photo-library picker
 * (expo-image-picker) and a file picker (expo-document-picker, for an existing PDF)
 * are offered — the backend's DOCUMENT upload slot accepts PDF/PNG/JPEG (ADR-118).
 */
function DocumentUploadForm({
  organizationId,
  participantId,
  requirementId,
}: {
  organizationId: string | null;
  participantId: string | null;
  requirementId: string;
}) {
  const requestUpload = useRequestMediaUpload(organizationId ?? '');
  const confirmUpload = useConfirmMediaUpload(organizationId ?? '');
  const submitEvidence = useSubmitGuardianEvidence(organizationId, participantId);
  const toast = useToast();
  const [uploading, setUploading] = useState(false);

  async function upload(file: PickedFile) {
    if (!organizationId || !participantId) return;
    if (file.fileSizeBytes > MAX_DOCUMENT_BYTES) {
      toast.show('That file is too large (max 15 MB).', 'error');
      return;
    }
    setUploading(true);
    try {
      const requested = await requestUpload.mutateAsync({
        usageSlot: 'DOCUMENT',
        fileName: file.fileName,
        contentType: file.mimeType,
        fileSizeBytes: file.fileSizeBytes,
        entityType: 'PARTICIPANT',
        entityId: participantId,
      });
      await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
      const confirmed = await confirmUpload.mutateAsync(requested.assetId);
      if (confirmed.status === 'REJECTED') {
        toast.show(
          (confirmed.rejectionReason && REJECTION_MESSAGES[confirmed.rejectionReason]) ?? 'That file could not be used. Please try another.',
          'error',
        );
        return;
      }
      await submitEvidence.mutateAsync({ requirementId, acceptanceMethod: 'FILE_UPLOAD', documentAssetId: requested.assetId });
    } catch {
      toast.show('Could not upload that file. Please try again.', 'error');
    } finally {
      setUploading(false);
    }
  }

  async function onTakePhoto() {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      toast.show('Camera access is needed to photograph a document.', 'error');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({ quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await upload({
      uri: asset.uri,
      fileName: asset.fileName ?? 'document.jpg',
      mimeType: asset.mimeType ?? 'image/jpeg',
      fileSizeBytes: asset.fileSize ?? 0,
    });
  }

  async function onChooseFromLibrary() {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      toast.show('Photo library access is needed to choose a document photo.', 'error');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await upload({
      uri: asset.uri,
      fileName: asset.fileName ?? 'document.jpg',
      mimeType: asset.mimeType ?? 'image/jpeg',
      fileSizeBytes: asset.fileSize ?? 0,
    });
  }

  async function onChoosePdf() {
    const result = await DocumentPicker.getDocumentAsync({ type: 'application/pdf' });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await upload({
      uri: asset.uri,
      fileName: asset.name,
      mimeType: asset.mimeType ?? 'application/pdf',
      fileSizeBytes: asset.size ?? 0,
    });
  }

  if (uploading) {
    return (
      <View style={[styles.inlineForm, styles.uploadingRow]}>
        <ActivityIndicator />
        <ThemedText type="small" themeColor="textSecondary">
          Uploading…
        </ThemedText>
      </View>
    );
  }

  return (
    <View style={styles.inlineForm}>
      <ThemedText type="small" themeColor="textSecondary">
        Upload a PDF, or take or choose a photo of the document.
      </ThemedText>
      <View style={styles.uploadButtonRow}>
        <Button variant="secondary" style={styles.uploadButton} onPress={() => void onTakePhoto()}>
          Take Photo
        </Button>
        <Button variant="secondary" style={styles.uploadButton} onPress={() => void onChooseFromLibrary()}>
          Choose Photo
        </Button>
        <Button variant="secondary" style={styles.uploadButton} onPress={() => void onChoosePdf()}>
          Choose PDF
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
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
  uploadButtonRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  uploadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
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

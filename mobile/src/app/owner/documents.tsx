import Ionicons from '@expo/vector-icons/Ionicons';
import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import {
  useAssignOrganizationDocument,
  useBroadcastDocumentToAllHouseholds,
  useOrganizationDocuments,
  useRemoveDocument,
} from '@/features/documents/api';
import type { DocumentResponse } from '@/features/documents/types';
import { useConfirmMediaUpload, useRequestMediaUpload } from '@/features/media/api';
import { uploadToSignedUrl, type PickedFile } from '@/features/media/uploadToSignedUrl';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const MAX_DOCUMENT_BYTES = 15 * 1024 * 1024;

const REJECTION_MESSAGES: Record<string, string> = {
  FILE_TOO_LARGE: 'That file is too large (max 15 MB). Try a smaller photo or file.',
  UNRECOGNIZED_FILE_FORMAT: "That file type isn't supported. Use a PDF or a photo (JPEG/PNG).",
  CONTENT_TYPE_MISMATCH: "That file's contents didn't match its file type. Try picking it again.",
  INVALID_IMAGE: "That photo couldn't be read. Try picking it again.",
  IMAGE_DIMENSIONS_TOO_LARGE: "That photo's resolution is too large. Try a smaller photo.",
};

/**
 * Owner-side organization document management (Phase 37.12, ADR-119) — mirrors
 * frontend/src/features/documents/OrganizationDocumentsPanel.tsx. Reuses the exact
 * mobile/src/features/media upload trio built for eligibility (Phase 37.9); org-level
 * assignment needs no entityType/entityId (MediaUploadService.requestUpload falls back
 * to a manager-role check when both are omitted — confirmed against the backend, not
 * assumed), so this is the same DOCUMENT usage slot with a simpler call.
 */
export default function OwnerDocumentsScreen() {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const documentsQuery = useOrganizationDocuments(organizationId);
  const removeDocument = useRemoveDocument(organizationId);
  const toast = useToast();
  const [uploadMode, setUploadMode] = useState<'closed' | 'add' | 'broadcast'>('closed');

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Documents" />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.actionRow}>
          <Button variant="secondary" style={styles.actionButton} onPress={() => setUploadMode(uploadMode === 'add' ? 'closed' : 'add')}>
            Add Document
          </Button>
          <Button
            variant="secondary"
            style={styles.actionButton}
            onPress={() => setUploadMode(uploadMode === 'broadcast' ? 'closed' : 'broadcast')}>
            Send to Every Household
          </Button>
        </View>

        {uploadMode !== 'closed' && (
          <DocumentUploadForm
            organizationId={organizationId}
            broadcast={uploadMode === 'broadcast'}
            onDone={() => setUploadMode('closed')}
          />
        )}

        {documentsQuery.isLoading && <LoadingState label="Loading documents…" />}
        {documentsQuery.isError && <ErrorState message="Could not load documents." onRetry={() => documentsQuery.refetch()} />}
        {documentsQuery.data && documentsQuery.data.items.length === 0 && (
          <EmptyState title="No documents yet" description="Add a document for your organization or send one to every household." />
        )}
        <View style={styles.list}>
          {documentsQuery.data?.items.map((document) => (
            <DocumentRow
              key={document.id}
              document={document}
              onRemove={() =>
                Alert.alert('Remove document', `Remove "${document.title ?? 'this document'}"?`, [
                  { text: 'Cancel', style: 'cancel' },
                  {
                    text: 'Remove',
                    style: 'destructive',
                    onPress: () =>
                      removeDocument.mutate(document.id, {
                        onSuccess: () => toast.show('Document removed.', 'success'),
                        onError: () => toast.show('Could not remove that. Please try again.', 'error'),
                      }),
                  },
                ])
              }
            />
          ))}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

function DocumentRow({ document, onRemove }: { document: DocumentResponse; onRemove: () => void }) {
  const theme = useTheme();
  return (
    <ThemedView type="backgroundElement" style={styles.row}>
      <Ionicons name="document-text" size={22} color={theme.text} />
      <View style={styles.rowBody}>
        <ThemedText type="smallBold">{document.title ?? 'Document'}</ThemedText>
        {document.byteSizeBytes != null && (
          <ThemedText type="small" themeColor="textSecondary">
            {Math.round(document.byteSizeBytes / 1024)} KB
          </ThemedText>
        )}
      </View>
      <Button variant="secondary" onPress={onRemove}>
        Remove
      </Button>
    </ThemedView>
  );
}

function DocumentUploadForm({
  organizationId,
  broadcast,
  onDone,
}: {
  organizationId: string | null;
  broadcast: boolean;
  onDone: () => void;
}) {
  const theme = useTheme();
  const toast = useToast();
  const requestUpload = useRequestMediaUpload(organizationId ?? '');
  const confirmUpload = useConfirmMediaUpload(organizationId ?? '');
  const assignDocument = useAssignOrganizationDocument(organizationId);
  const broadcastDocument = useBroadcastDocumentToAllHouseholds(organizationId);
  const [title, setTitle] = useState('');
  const [uploading, setUploading] = useState(false);

  async function upload(file: PickedFile) {
    if (!organizationId) return;
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
      const params = { assetId: requested.assetId, title: title.trim() || undefined };
      if (broadcast) {
        await broadcastDocument.mutateAsync(params);
        toast.show('Document sent to every household.', 'success');
      } else {
        await assignDocument.mutateAsync(params);
        toast.show('Document added.', 'success');
      }
      onDone();
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
    await upload({ uri: asset.uri, fileName: asset.fileName ?? 'document.jpg', mimeType: asset.mimeType ?? 'image/jpeg', fileSizeBytes: asset.fileSize ?? 0 });
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
    await upload({ uri: asset.uri, fileName: asset.fileName ?? 'document.jpg', mimeType: asset.mimeType ?? 'image/jpeg', fileSizeBytes: asset.fileSize ?? 0 });
  }

  async function onChoosePdf() {
    const result = await DocumentPicker.getDocumentAsync({ type: 'application/pdf' });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await upload({ uri: asset.uri, fileName: asset.name, mimeType: asset.mimeType ?? 'application/pdf', fileSizeBytes: asset.size ?? 0 });
  }

  if (uploading) {
    return (
      <View style={[styles.uploadForm, styles.uploadingRow]}>
        <ActivityIndicator />
        <ThemedText type="small" themeColor="textSecondary">
          Uploading…
        </ThemedText>
      </View>
    );
  }

  return (
    <View style={styles.uploadForm}>
      <ThemedText type="small" themeColor="textSecondary">
        {broadcast ? 'This document will be sent to every household in your organization.' : 'Upload a PDF, or take or choose a photo.'}
      </ThemedText>
      <TextInput
        value={title}
        onChangeText={setTitle}
        placeholder="Document title (optional)"
        placeholderTextColor={theme.textSecondary}
        style={[styles.input, { color: theme.text, backgroundColor: theme.background }]}
      />
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
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  actionRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  actionButton: {
    flex: 1,
  },
  uploadForm: {
    gap: Spacing.two,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    backgroundColor: 'rgba(127,127,127,0.08)',
  },
  uploadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  input: {
    minHeight: 44,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
  },
  uploadButtonRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  uploadButton: {
    alignSelf: 'flex-start',
  },
  list: {
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
});

import { useLocalSearchParams } from 'expo-router';
import { Linking, Pressable, ScrollView, Share, StyleSheet, TextInput, useWindowDimensions, View } from 'react-native';
import { useState } from 'react';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import {
  useCloseFundraisingGame,
  useCreateFundraisingGame,
  useDrawFundraisingGameWinner,
  useFundraisingGame,
  useFundraisingGameEntries,
  useOpenFundraisingGame,
  useUpdateFundraisingGame,
} from '@/features/fundraisingGames/api';
import type { FundraisingGame, FundraisingGameType } from '@/features/fundraisingGames/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { env } from '@/lib/env';

const GAME_TYPES: { value: FundraisingGameType; label: string; description: string }[] = [
  { value: 'BIG_GAME_SQUARES', label: 'Big Game Squares', description: 'Supporters choose an open square for free.' },
  { value: 'BRACKET_CHALLENGE', label: 'Bracket Challenge', description: 'Free bracket/pick challenge attached to the fundraiser.' },
  { value: 'PREDICTION_CHALLENGE', label: 'Prediction Challenge', description: 'Supporters make a free game/score prediction.' },
  { value: 'FREE_PRIZE_DRAWING', label: 'Free Prize Drawing', description: 'Free entry, with optional fixed sponsor/organization prize.' },
  { value: 'TRIVIA_CHALLENGE', label: 'Trivia Challenge', description: 'Free-answer sports or team trivia challenge.' },
];

export default function FundraisingGameScreen() {
  const { organizationId = '', campaignId = '', slug = '' } = useLocalSearchParams<{ organizationId: string; campaignId: string; slug: string }>();
  const game = useFundraisingGame(organizationId || null, campaignId || null);
  if (game.isLoading) return <><ScreenHeader title="Free Fundraising Game" /><LoadingState label="Loading game…" /></>;
  if (game.isError) return <><ScreenHeader title="Free Fundraising Game" /><ErrorState message="Could not load this fundraiser's free game." onRetry={() => game.refetch()} /></>;
  return <GameEditor key={game.data?.id ?? 'new-game'} organizationId={organizationId} campaignId={campaignId} slug={slug} existing={game.data ?? null} />;
}

function GameEditor({ organizationId, campaignId, slug, existing }: { organizationId: string; campaignId: string; slug: string; existing: FundraisingGame | null }) {
  const theme = useTheme();
  const toast = useToast();
  const { width } = useWindowDimensions();
  const create = useCreateFundraisingGame(organizationId, campaignId);
  const update = useUpdateFundraisingGame(organizationId, campaignId);
  const open = useOpenFundraisingGame(organizationId, campaignId);
  const close = useCloseFundraisingGame(organizationId, campaignId);
  const drawWinner = useDrawFundraisingGameWinner(organizationId, campaignId);
  const entries = useFundraisingGameEntries(organizationId, campaignId, existing?.id || null);
  const [gameType, setGameType] = useState<FundraisingGameType>(existing?.gameType ?? 'BIG_GAME_SQUARES');
  const [title, setTitle] = useState(existing?.title ?? 'Big Game Squares Challenge');
  const [instructions, setInstructions] = useState(existing?.instructions ?? 'Choose an open square for free. Supporting the fundraiser is optional and never changes your odds or number of entries.');
  const [prizeDescription, setPrizeDescription] = useState(existing?.prizeDescription ?? 'Bragging rights / sponsor-provided recognition');
  const [maxEntries, setMaxEntries] = useState(existing?.maxEntries == null ? '' : String(existing.maxEntries));
  const [entriesPerPerson, setEntriesPerPerson] = useState(String(existing?.entriesPerPerson ?? 1));
  const [rows, setRows] = useState(String(existing?.rows ?? 10));
  const [cols, setCols] = useState(String(existing?.cols ?? 10));
  const wide = width >= 760;
  const disclosure = 'No purchase or donation is necessary to enter. Donating does not improve your odds or provide additional entries.';
  const publicGameUrl = `${env.frontendBaseUrl}/campaigns/${slug}/play`;
  const canConfigure = !existing || (existing.permissions?.canEdit ?? false);
  const busy = create.isPending || update.isPending || open.isPending || close.isPending || drawWinner.isPending;

  function selectType(value: FundraisingGameType) {
    setGameType(value);
    const option = GAME_TYPES.find((item) => item.value === value);
    if (!existing && option) setTitle(option.label);
  }

  async function save() {
    const perPerson = Number(entriesPerPerson);
    const limit = maxEntries.trim() ? Number(maxEntries) : null;
    const r = gameType === 'BIG_GAME_SQUARES' ? Number(rows) : null;
    const c = gameType === 'BIG_GAME_SQUARES' ? Number(cols) : null;
    if (!title.trim()) return toast.show('Game title is required.', 'error');
    if (!Number.isInteger(perPerson) || perPerson < 1 || perPerson > 20) return toast.show('Free entries per person must be between 1 and 20.', 'error');
    if (limit != null && (!Number.isInteger(limit) || limit < 1)) return toast.show('Entry limit must be a positive whole number, or leave it blank for unlimited.', 'error');
    if (gameType === 'BIG_GAME_SQUARES' && (!Number.isInteger(r) || !Number.isInteger(c) || !r || !c || r < 1 || c < 1 || r > 26 || c > 26)) return toast.show('Squares grids can be 1–26 rows and columns.', 'error');
    try {
      if (existing) {
        await update.mutateAsync({ gameId: existing.id, data: { title: title.trim(), instructions: instructions.trim() || null, prizeDescription: prizeDescription.trim() || null, maxEntries: limit, entriesPerPerson: perPerson, rows: r, cols: c } });
        toast.show('Free game updated.', 'success');
      } else {
        await create.mutateAsync({ gameType, title: title.trim(), instructions: instructions.trim() || null, prizeDescription: prizeDescription.trim() || null, maxEntries: limit, entriesPerPerson: perPerson, rows: r, cols: c });
        toast.show('Free game created.', 'success');
      }
    } catch {
      toast.show('Could not save this free game.', 'error');
    }
  }

  async function shareGame() {
    try { await Share.share({ message: `${title}\nFree to play — no purchase necessary.\n${publicGameUrl}`, url: publicGameUrl }); }
    catch { toast.show('Could not open sharing.', 'error'); }
  }

  async function doAction(success: string, action: () => Promise<unknown>) {
    try { await action(); toast.show(success, 'success'); }
    catch { toast.show('That free-game action could not be completed.', 'error'); }
  }

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Free Fundraising Game" />
      <ScrollView contentContainerStyle={[styles.content, wide && styles.contentWide]} keyboardShouldPersistTaps="handled">
        <ThemedView type="backgroundElement" style={styles.disclosure}>
          <ThemedText type="smallBold">Free-entry rule</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">{disclosure}</ThemedText>
        </ThemedView>

        {!existing && <Field label="Game type">
          <View style={[styles.typeGrid, wide && styles.typeGridWide]}>
            {GAME_TYPES.map((option) => <Pressable key={option.value} onPress={() => selectType(option.value)} style={[styles.typeCard, wide && styles.typeCardWide, gameType === option.value && styles.typeCardSelected]}>
              <ThemedText type="smallBold" style={gameType === option.value ? styles.selectedText : undefined}>{option.label}</ThemedText>
              <ThemedText type="small" themeColor={gameType === option.value ? undefined : 'textSecondary'} style={gameType === option.value ? styles.selectedText : undefined}>{option.description}</ThemedText>
            </Pressable>)}
          </View>
        </Field>}

        {existing && <View style={styles.statusRow}>
          <View style={styles.flexOne}><ThemedText type="subtitle">{existing.title}</ThemedText><ThemedText type="small" themeColor="textSecondary">{existing.status} · {existing.entryCount} entries</ThemedText></View>
          <ThemedText type="smallBold" style={styles.freeBadge}>FREE</ThemedText>
        </View>}

        {canConfigure && <>
          <Field label="Title"><TextInput value={title} onChangeText={setTitle} maxLength={160} placeholder="Free game title" placeholderTextColor={theme.textSecondary} style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field>
          <Field label="Instructions"><TextInput value={instructions} onChangeText={setInstructions} multiline maxLength={3000} textAlignVertical="top" placeholder="How to play…" placeholderTextColor={theme.textSecondary} style={[styles.input, styles.multiline, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field>
          <Field label="Prize / recognition (optional)"><TextInput value={prizeDescription} onChangeText={setPrizeDescription} multiline maxLength={1000} textAlignVertical="top" placeholder="Use a fixed sponsor/organization prize or recognition — not a percentage of donations." placeholderTextColor={theme.textSecondary} style={[styles.input, styles.smallMultiline, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field>
          <Field label="Free entries per person"><TextInput value={entriesPerPerson} onChangeText={setEntriesPerPerson} keyboardType="number-pad" style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field>
          {gameType !== 'BIG_GAME_SQUARES' && <Field label="Total entry limit"><TextInput value={maxEntries} onChangeText={setMaxEntries} keyboardType="number-pad" placeholder="Leave blank for unlimited" placeholderTextColor={theme.textSecondary} style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field>}
          {gameType === 'BIG_GAME_SQUARES' && <View style={styles.rowFields}><View style={styles.half}><Field label="Rows"><TextInput value={rows} onChangeText={setRows} keyboardType="number-pad" style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field></View><View style={styles.half}><Field label="Columns"><TextInput value={cols} onChangeText={setCols} keyboardType="number-pad" style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} /></Field></View></View>}
          <Button disabled={busy} onPress={save}>{existing ? 'Save Game Setup' : 'Create Free Game'}</Button>
        </>}

        {existing && <ThemedView type="backgroundElement" style={styles.actionsCard}>
          <View style={styles.flexOne}><ThemedText type="smallBold">Game actions</ThemedText><ThemedText type="small" themeColor="textSecondary">Opening a game allows public free entries. Close it before a random prize drawing is selected.</ThemedText></View>
          <View style={styles.actions}>
            {existing.permissions?.canOpen && <Button disabled={busy} onPress={() => doAction('Free game opened.', () => open.mutateAsync(existing.id))}>Open Game</Button>}
            {existing.permissions?.canClose && <Button variant="secondary" disabled={busy} onPress={() => doAction('Free game closed.', () => close.mutateAsync(existing.id))}>Close Game</Button>}
            {existing.permissions?.canDrawWinner && <Button disabled={busy} onPress={() => doAction('Winner selected.', () => drawWinner.mutateAsync({ gameId: existing.id, drawId: '' }))}>Draw Winner</Button>}
          </View>
        </ThemedView>}

        {existing && <View style={styles.actions}>
          <Button variant="secondary" onPress={shareGame}>Share Free Game</Button>
          <Button variant="secondary" onPress={() => Linking.openURL(publicGameUrl)}>Open Public Game</Button>
        </View>}

        {existing && <>
          <View style={styles.sectionHeader}><ThemedText type="smallBold">Entries</ThemedText><ThemedText type="small" themeColor="textSecondary">{entries.data?.length ?? existing.entryCount}</ThemedText></View>
          {entries.isLoading && <LoadingState label="Loading entries…" />}
          {entries.isError && <ErrorState message="Could not load free-game entries." onRetry={() => entries.refetch()} />}
          {entries.data?.length === 0 && <ThemedView type="backgroundElement" style={styles.empty}><ThemedText type="small" themeColor="textSecondary">No free entries yet.</ThemedText></ThemedView>}
          {entries.data?.map((entry: any) => <ThemedView key={entry.id} type="backgroundElement" style={[styles.entryRow, entry.isWinner && styles.winnerRow]}>
            <View style={styles.flexOne}><ThemedText type="smallBold">{entry.displayName}{entry.isWinner ? ' · Winner' : ''}</ThemedText><ThemedText type="small" themeColor="textSecondary">{entry.email}</ThemedText>{entry.selectionKey && <ThemedText type="small" themeColor="textSecondary">Square: {entry.selectionKey}</ThemedText>}{entry.selectionText && <ThemedText type="small" themeColor="textSecondary">Pick: {entry.selectionText}</ThemedText>}</View>
          </ThemedView>)}
        </>}
      </ScrollView>
    </ThemedView>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) { return <View style={styles.field}><ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>{children}</View>; }

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { width: '100%', alignSelf: 'center', paddingHorizontal: Spacing.four, paddingBottom: Spacing.six, gap: Spacing.three },
  contentWide: { maxWidth: 920 },
  disclosure: { borderRadius: Spacing.three, borderWidth: 1, borderColor: Brand.victoryGreen, padding: Spacing.three, gap: Spacing.one },
  field: { gap: Spacing.one },
  input: { minHeight: 46, borderRadius: Spacing.two, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  multiline: { minHeight: 110 },
  smallMultiline: { minHeight: 78 },
  typeGrid: { gap: Spacing.two },
  typeGridWide: { flexDirection: 'row', flexWrap: 'wrap' },
  typeCard: { borderWidth: 1, borderColor: Brand.slateGray, borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.one },
  typeCardWide: { width: '48.5%' },
  typeCardSelected: { backgroundColor: Brand.championshipGold, borderColor: Brand.championshipGold },
  selectedText: { color: Brand.navy },
  statusRow: { flexDirection: 'row', gap: Spacing.three, alignItems: 'center' },
  freeBadge: { color: Brand.victoryGreen },
  flexOne: { flex: 1 },
  rowFields: { flexDirection: 'row', gap: Spacing.two },
  half: { flex: 1 },
  actionsCard: { borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.three },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.two },
  entryRow: { borderRadius: Spacing.two, padding: Spacing.three },
  winnerRow: { borderWidth: 1, borderColor: Brand.championshipGold },
  empty: { borderRadius: Spacing.two, padding: Spacing.four, alignItems: 'center' },
});

import DateTimePicker from '@react-native-community/datetimepicker';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, useWindowDimensions, View } from 'react-native';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useCampaign, useCreateCampaign, useUpdateCampaign } from '@/features/fundraising/api';
import type { Campaign, CampaignType, FundraiserTemplateKey, FundraisingPersona } from '@/features/fundraising/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

interface TemplateOption {
  key: FundraiserTemplateKey;
  label: string;
  description: string;
  campaignType: CampaignType;
  title?: string;
  starterDescription?: string;
  inPerson?: boolean;
}

const TEMPLATES: TemplateOption[] = [
  { key: 'GENERAL', label: 'General', description: 'Flexible fundraiser for any club or team need.', campaignType: 'ORGANIZATION_GENERAL' },
  { key: 'IN_PERSON_EVENT', label: 'In-person event', description: 'Sale, clinic, dinner, community event, and more.', campaignType: 'SPECIAL_EVENTS', inPerson: true },
  { key: 'SPONSOR_MATCH', label: 'Sponsor match', description: 'A sponsor helps amplify supporter donations.', campaignType: 'SPONSOR_SUPPORTED', title: 'Community Match Challenge', starterDescription: 'Every contribution helps us move closer to our goal. A community sponsor is helping amplify supporter giving.' },
  { key: 'MILESTONE_CHALLENGE', label: 'Milestone challenge', description: 'Unlock fun team or coach challenges at fundraising milestones.', campaignType: 'SPECIAL_EVENTS', title: 'Team Milestone Challenge', starterDescription: 'Help us unlock fun team milestones as we work toward our fundraising goal.' },
  { key: 'FUNDRAISING_CHALLENGE', label: 'Team / family challenge', description: 'Make attributed fundraising progress fun for families and teams.', campaignType: 'TEAM_GENERAL', title: 'Team Fundraising Challenge', starterDescription: 'Share your Rally26 link and help the team reach our goal together.' },
  { key: 'BAKE_SALE', label: 'Bake sale', description: 'Starter for an in-person community bake sale.', campaignType: 'SPECIAL_EVENTS', title: 'Bake Sale', starterDescription: 'Homemade treats — proceeds support the team. Come by, donate, and help us reach our goal!', inPerson: true },
  { key: 'CAR_WASH', label: 'Car wash', description: 'Starter for a team car wash.', campaignType: 'SPECIAL_EVENTS', title: 'Car Wash', starterDescription: 'Bring your car by for a wash — all proceeds support the team. Volunteers welcome!', inPerson: true },
];

type PickerTarget = 'start' | 'end' | null;

export default function FundraisingFormScreen() {
  const { organizationId = '', campaignId = '', mode = 'create', persona = 'parent', defaultTeamId = '', defaultTeamName = '' } = useLocalSearchParams<{
    organizationId: string;
    campaignId?: string;
    mode?: 'create' | 'edit';
    persona?: FundraisingPersona;
    defaultTeamId?: string;
    defaultTeamName?: string;
  }>();
  const isEdit = mode === 'edit';
  const existing = useCampaign(isEdit ? organizationId : null, isEdit ? campaignId : null);

  if (isEdit && existing.isLoading) return <><ScreenHeader title="Edit Fundraiser" /><LoadingState label="Loading fundraiser…" /></>;
  if (isEdit && (existing.isError || !existing.data)) return <><ScreenHeader title="Edit Fundraiser" /><ErrorState message="Could not load this fundraiser." onRetry={() => existing.refetch()} /></>;

  return (
    <FundraisingFormFields
      key={isEdit ? existing.data?.id : 'create'}
      organizationId={organizationId}
      campaignId={isEdit ? campaignId : null}
      persona={persona}
      existing={existing.data ?? null}
      defaultTeamId={defaultTeamId}
      defaultTeamName={defaultTeamName}
    />
  );
}

function FundraisingFormFields({ organizationId, campaignId, persona, existing, defaultTeamId, defaultTeamName }: {
  organizationId: string;
  campaignId: string | null;
  persona: FundraisingPersona;
  existing: Campaign | null;
  defaultTeamId: string;
  defaultTeamName: string;
}) {
  const isEdit = !!existing;
  const theme = useTheme();
  const toast = useToast();
  const { width } = useWindowDimensions();
  const createCampaign = useCreateCampaign(organizationId || null);
  const updateCampaign = useUpdateCampaign(organizationId || null, campaignId);
  const initialTemplate = TEMPLATES.find((template) => template.key === existing?.templateKey) ?? TEMPLATES[0];
  const [templateKey, setTemplateKey] = useState<FundraiserTemplateKey>(initialTemplate.key);
  const [campaignType, setCampaignType] = useState<CampaignType>(existing?.campaignType ?? initialTemplate.campaignType);
  const [name, setName] = useState(existing?.name ?? initialTemplate.title ?? '');
  const [slug, setSlug] = useState(existing?.slug ?? '');
  const [slugTouched, setSlugTouched] = useState(isEdit);
  const [description, setDescription] = useState(existing?.description ?? initialTemplate.starterDescription ?? '');
  const [goalDollars, setGoalDollars] = useState(existing ? String(existing.goalAmountMinor / 100) : '');
  const [startDate, setStartDate] = useState(() => existing?.startDate ? dateFromIso(existing.startDate) : todayAtNoon());
  const [endDate, setEndDate] = useState<Date | null>(() => existing?.endDate ? dateFromIso(existing.endDate) : addDays(todayAtNoon(), 30));
  const [picker, setPicker] = useState<PickerTarget>(null);
  const [eventLocationName, setEventLocationName] = useState(existing?.eventLocationName ?? '');
  const [eventAddress, setEventAddress] = useState(existing?.eventAddress ?? '');
  const [useCoachTeam, setUseCoachTeam] = useState(!!defaultTeamId && (existing?.teamId ? existing.teamId === defaultTeamId : true));
  const selectedTemplate = TEMPLATES.find((template) => template.key === templateKey) ?? TEMPLATES[0];
  const wide = width >= 760;

  function applyTemplate(template: TemplateOption) {
    setTemplateKey(template.key);
    setCampaignType(template.campaignType);
    if (!isEdit) {
      if (template.title) updateName(template.title);
      if (template.starterDescription) setDescription(template.starterDescription);
    }
  }

  function updateName(value: string) {
    setName(value);
    if (!isEdit && !slugTouched) setSlug(slugify(value));
  }

  function duration(days: number) {
    setEndDate(addDays(startDate, days));
  }

  async function submit() {
    const goal = Number(goalDollars);
    if (!name.trim()) return toast.show('Fundraiser name is required.', 'error');
    if (!isEdit && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug.trim())) return toast.show('Use a simple lowercase fundraiser URL slug.', 'error');
    if (!Number.isFinite(goal) || goal < 0) return toast.show('Enter a valid fundraising goal.', 'error');
    if (endDate && endDate < startDate) return toast.show('End date must be on or after the start date.', 'error');
    if (selectedTemplate.inPerson && !eventLocationName.trim() && !eventAddress.trim()) return toast.show('Add a venue or address for this in-person fundraiser.', 'error');

    try {
      if (isEdit) {
        await updateCampaign.mutateAsync({
          name: name.trim(),
          description: description.trim() || null,
          goalAmountMinor: Math.round(goal * 100),
          startDate: isoDate(startDate),
          endDate: endDate ? isoDate(endDate) : null,
          eventLocationName: eventLocationName.trim() || null,
          eventAddress: eventAddress.trim() || null,
        });
        toast.show('Fundraiser updated.', 'success');
        router.back();
      } else {
        const created = await createCampaign.mutateAsync({
          teamId: useCoachTeam && defaultTeamId ? defaultTeamId : null,
          name: name.trim(),
          slug: slug.trim(),
          description: description.trim() || null,
          campaignType,
          goalAmountMinor: Math.round(goal * 100),
          currency: 'USD',
          startDate: isoDate(startDate),
          endDate: endDate ? isoDate(endDate) : null,
          eventLocationName: eventLocationName.trim() || null,
          eventAddress: eventAddress.trim() || null,
          templateKey,
        });
        toast.show('Fundraiser created.', 'success');
        router.replace({ pathname: '/fundraising-detail' as any, params: { organizationId, campaignId: created.id, persona, defaultTeamId, defaultTeamName } });
      }
    } catch {
      toast.show(isEdit ? 'Could not update that fundraiser.' : 'Could not create that fundraiser.', 'error');
    }
  }

  const saving = createCampaign.isPending || updateCampaign.isPending;
  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScreenHeader title={isEdit ? 'Edit Fundraiser' : 'New Fundraiser'} />
      <ScrollView contentContainerStyle={[styles.content, wide && styles.contentWide]} keyboardShouldPersistTaps="handled">
        {!isEdit && (
          <Field label="Choose a template">
            <View style={[styles.templateGrid, wide && styles.templateGridWide]}>
              {TEMPLATES.map((template) => {
                const selected = template.key === templateKey;
                return (
                  <Pressable key={template.key} onPress={() => applyTemplate(template)} style={[styles.templateCard, selected && styles.templateCardSelected, wide && styles.templateCardWide]}>
                    <ThemedText type="smallBold" style={selected ? styles.selectedText : undefined}>{template.label}</ThemedText>
                    <ThemedText type="small" themeColor={selected ? undefined : 'textSecondary'} style={selected ? styles.selectedText : undefined}>{template.description}</ThemedText>
                  </Pressable>
                );
              })}
            </View>
          </Field>
        )}

        {persona === 'coach' && defaultTeamId && !isEdit && (
          <Field label="Fundraiser scope">
            <View style={styles.chips}>
              <Choice label="Organization-wide" selected={!useCoachTeam} onPress={() => setUseCoachTeam(false)} />
              <Choice label={defaultTeamName || 'Selected team'} selected={useCoachTeam} onPress={() => setUseCoachTeam(true)} />
            </View>
          </Field>
        )}

        <Field label="Fundraiser name">
          <TextInput value={name} onChangeText={updateName} placeholder="14U Nationals Travel Fund" placeholderTextColor={theme.textSecondary} style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} maxLength={160} />
        </Field>
        {!isEdit && <Field label="Public URL slug">
          <TextInput value={slug} onChangeText={(value) => { setSlugTouched(true); setSlug(slugify(value)); }} autoCapitalize="none" autoCorrect={false} placeholder="14u-nationals" placeholderTextColor={theme.textSecondary} style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} />
          <ThemedText type="small" themeColor="textSecondary">rally26.com/campaigns/{slug || 'your-fundraiser'}</ThemedText>
        </Field>}
        <Field label="Description">
          <TextInput value={description} onChangeText={setDescription} multiline maxLength={3000} textAlignVertical="top" placeholder="Tell supporters what you're raising money for…" placeholderTextColor={theme.textSecondary} style={[styles.input, styles.multiline, { color: theme.text, backgroundColor: theme.backgroundElement }]} />
        </Field>
        <Field label="Fundraising goal">
          <View style={[styles.moneyRow, { backgroundColor: theme.backgroundElement }]}><ThemedText>$</ThemedText><TextInput value={goalDollars} onChangeText={setGoalDollars} keyboardType="decimal-pad" placeholder="5000" placeholderTextColor={theme.textSecondary} style={[styles.moneyInput, { color: theme.text }]} /></View>
        </Field>

        <Field label="Start date">
          <DateButton value={startDate.toLocaleDateString()} onPress={() => setPicker('start')} />
        </Field>
        <Field label="Duration">
          <View style={styles.chips}>
            {[7, 30, 90].map((days) => <Choice key={days} label={`${days} days`} selected={!!endDate && sameDay(endDate, addDays(startDate, days))} onPress={() => duration(days)} />)}
            <Choice label="Custom" selected={!!endDate && ![7, 30, 90].some((days) => sameDay(endDate, addDays(startDate, days)))} onPress={() => setPicker('end')} />
            <Choice label="No end date" selected={!endDate} onPress={() => setEndDate(null)} />
          </View>
          {endDate && <Pressable onPress={() => setPicker('end')} style={styles.endDateLine}><ThemedText type="small">Ends {endDate.toLocaleDateString()}</ThemedText></Pressable>}
        </Field>
        {picker && <DateTimePicker value={picker === 'start' ? startDate : (endDate ?? startDate)} mode="date" display={Platform.OS === 'ios' ? 'spinner' : 'default'} onChange={(_event, selected) => {
          const target = picker;
          setPicker(null);
          if (!selected) return;
          if (target === 'start') setStartDate(selected);
          else setEndDate(selected);
        }} />}

        {selectedTemplate.inPerson && <ThemedView type="backgroundElement" style={styles.locationPanel}>
          <ThemedText type="smallBold">In-person event location</ThemedText>
          <Field label="Venue / location name">
            <TextInput value={eventLocationName} onChangeText={setEventLocationName} placeholder="Community Center" placeholderTextColor={theme.textSecondary} style={[styles.input, { color: theme.text, backgroundColor: theme.background }]} maxLength={200} />
          </Field>
          <Field label="Address">
            <TextInput value={eventAddress} onChangeText={setEventAddress} placeholder="123 Main St, Town, NJ" placeholderTextColor={theme.textSecondary} style={[styles.input, { color: theme.text, backgroundColor: theme.background }]} maxLength={500} />
          </Field>
        </ThemedView>}
      </ScrollView>
      <View style={styles.footer}><Button disabled={saving} onPress={submit}>{saving ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Fundraiser'}</Button></View>
    </KeyboardAvoidingView>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <View style={styles.field}><ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>{children}</View>;
}
function Choice({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return <Pressable onPress={onPress} style={[styles.choice, selected && styles.choiceSelected]}><ThemedText type="smallBold" style={selected ? styles.selectedText : undefined}>{label}</ThemedText></Pressable>;
}
function DateButton({ value, onPress }: { value: string; onPress: () => void }) {
  return <Pressable onPress={onPress} style={styles.dateButton}><ThemedText>{value}</ThemedText></Pressable>;
}
function slugify(value: string) { return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, ''); }
function todayAtNoon() { const date = new Date(); date.setHours(12, 0, 0, 0); return date; }
function dateFromIso(value: string) { return new Date(`${value}T12:00:00`); }
function addDays(value: Date, days: number) { const next = new Date(value); next.setDate(next.getDate() + days); return next; }
function isoDate(value: Date) { const y = value.getFullYear(); const m = String(value.getMonth() + 1).padStart(2, '0'); const d = String(value.getDate()).padStart(2, '0'); return `${y}-${m}-${d}`; }
function sameDay(a: Date, b: Date) { return isoDate(a) === isoDate(b); }

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { width: '100%', alignSelf: 'center', paddingHorizontal: Spacing.four, paddingBottom: Spacing.six },
  contentWide: { maxWidth: 900 },
  field: { gap: Spacing.one, marginTop: Spacing.three },
  input: { minHeight: 46, borderRadius: Spacing.two, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  multiline: { minHeight: 110 },
  templateGrid: { gap: Spacing.two },
  templateGridWide: { flexDirection: 'row', flexWrap: 'wrap' },
  templateCard: { borderWidth: 1, borderColor: Brand.slateGray, borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.one },
  templateCardWide: { width: '48.5%' },
  templateCardSelected: { backgroundColor: Brand.championshipGold, borderColor: Brand.championshipGold },
  selectedText: { color: Brand.navy },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  choice: { borderWidth: 1, borderColor: Brand.slateGray, borderRadius: 999, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  choiceSelected: { backgroundColor: Brand.championshipGold, borderColor: Brand.championshipGold },
  moneyRow: { flexDirection: 'row', alignItems: 'center', minHeight: 46, borderRadius: Spacing.two, paddingHorizontal: Spacing.three },
  moneyInput: { flex: 1, minHeight: 44, paddingHorizontal: Spacing.two, fontSize: 16 },
  dateButton: { minHeight: 46, justifyContent: 'center', borderWidth: 1, borderColor: Brand.slateGray, borderRadius: Spacing.two, paddingHorizontal: Spacing.three },
  endDateLine: { paddingTop: Spacing.one },
  locationPanel: { borderRadius: Spacing.three, padding: Spacing.three, marginTop: Spacing.four },
  footer: { paddingHorizontal: Spacing.four, paddingVertical: Spacing.three },
});

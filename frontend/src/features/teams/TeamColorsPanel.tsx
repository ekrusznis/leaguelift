import { useState } from "react";
import { Button } from "../../components/Button";
import { useUpdateTeamColors } from "./api";
import type { Team } from "./types";

const DEFAULT_PRIMARY_COLOR = "#0B1F33";
const DEFAULT_SECONDARY_COLOR = "#20B26B";

const PRESET_SWATCHES = ["#0B1F33", "#20B26B", "#F2600C", "#2F6FED", "#C93636", "#6B21A8", "#000000", "#FFFFFF"];

const HEX_PATTERN = /^#[0-9A-Fa-f]{6}$/;

function normalizeHex(value: string): string {
	const trimmed = value.trim();
	return trimmed.startsWith("#") ? trimmed : `#${trimmed}`;
}

function ColorField({
	label,
	value,
	defaultValue,
	onChange,
	onReset,
}: {
	label: string;
	value: string;
	defaultValue: string;
	onChange: (hex: string) => void;
	onReset: () => void;
}) {
	const [draft, setDraft] = useState(value);
	const isValid = HEX_PATTERN.test(draft);
	const isDefault = value.toLowerCase() === defaultValue.toLowerCase();

	function commit(next: string) {
		setDraft(next);
		if (HEX_PATTERN.test(next)) onChange(next);
	}

	return (
		<div className="flex flex-col gap-2 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
			<div className="flex items-center justify-between">
				<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">{label}</span>
				{!isDefault && (
					<button type="button" onClick={onReset} className="text-xs font-medium text-info-blue hover:underline">
						Reset to default
					</button>
				)}
			</div>
			<div className="flex items-center gap-3">
				<input
					type="color"
					value={isValid ? draft : value}
					onChange={(event) => commit(event.target.value)}
					aria-label={`${label} color picker`}
					className="size-10 shrink-0 cursor-pointer rounded-md border border-slate-gray/30 bg-transparent p-0.5"
				/>
				<input
					type="text"
					value={draft}
					onChange={(event) => setDraft(normalizeHex(event.target.value))}
					onBlur={() => {
						if (HEX_PATTERN.test(draft)) commit(draft);
						else setDraft(value);
					}}
					aria-label={`${label} hex value`}
					placeholder="#0B1F33"
					maxLength={7}
					className="w-28 rounded-md border border-slate-gray/30 px-2 py-1.5 font-mono text-sm text-navy dark:text-[#f8fafc]"
				/>
				{!isValid && <span className="text-xs text-error-red">Enter a 6-digit hex value.</span>}
			</div>
			<div className="flex flex-wrap gap-1.5">
				{PRESET_SWATCHES.map((swatch) => (
					<button
						key={swatch}
						type="button"
						onClick={() => commit(swatch)}
						aria-label={`Use ${swatch}`}
						className="size-6 rounded-full border border-slate-gray/30"
						style={{ backgroundColor: swatch }}
					/>
				))}
			</div>
		</div>
	);
}

export function TeamColorsPanel({ organizationId, team }: { organizationId: string; team: Team }) {
	const updateColors = useUpdateTeamColors(organizationId);
	const [primaryColor, setPrimaryColor] = useState(team.primaryColor);
	const [secondaryColor, setSecondaryColor] = useState(team.secondaryColor);

	const dirty = primaryColor !== team.primaryColor || secondaryColor !== team.secondaryColor;

	function save() {
		updateColors.mutate({
			teamId: team.id,
			primaryColor: primaryColor.toLowerCase() === DEFAULT_PRIMARY_COLOR.toLowerCase() ? null : primaryColor,
			secondaryColor: secondaryColor.toLowerCase() === DEFAULT_SECONDARY_COLOR.toLowerCase() ? null : secondaryColor,
		});
	}

	return (
		<div className="flex flex-col gap-4 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4">
			<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
				These colors appear on {team.name}'s Swag Shop storefront and public team page. Leave a color at its default to use
				Rally26's own brand colors.
			</p>

			<div className="grid gap-3 sm:grid-cols-2">
				<ColorField
					label="Primary color"
					value={primaryColor}
					defaultValue={DEFAULT_PRIMARY_COLOR}
					onChange={setPrimaryColor}
					onReset={() => setPrimaryColor(DEFAULT_PRIMARY_COLOR)}
				/>
				<ColorField
					label="Secondary color"
					value={secondaryColor}
					defaultValue={DEFAULT_SECONDARY_COLOR}
					onChange={setSecondaryColor}
					onReset={() => setSecondaryColor(DEFAULT_SECONDARY_COLOR)}
				/>
			</div>

			<div>
				<p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-gray dark:text-[#cbd5e1]">Live preview</p>
				<div className="overflow-hidden rounded-lg border border-slate-gray/20">
					<div className="flex items-center justify-between px-4 py-3" style={{ backgroundColor: primaryColor }}>
						<span className="flex items-center gap-2 text-sm font-bold text-white">
							<span
								className="flex size-8 items-center justify-center rounded-md text-xs font-bold"
								style={{ backgroundColor: secondaryColor, color: primaryColor }}
							>
								{team.name.slice(0, 2).toUpperCase()}
							</span>
							{team.name}
						</span>
						<span
							className="rounded-full px-3 py-1 text-xs font-semibold"
							style={{ backgroundColor: secondaryColor, color: primaryColor }}
						>
							Swag Shop
						</span>
					</div>
					<div className="bg-pure-white dark:bg-[#111827] p-4">
						<p className="text-xs font-semibold uppercase tracking-wide" style={{ color: primaryColor }}>
							Upcoming event
						</p>
						<p className="mt-1 text-sm font-semibold text-navy dark:text-[#f8fafc]">Saturday scrimmage vs. Riverside</p>
						<p className="mt-1 text-xs text-slate-gray dark:text-[#cbd5e1]">Sat, Sep 12 · 9:00 AM</p>
						<button
							type="button"
							disabled
							className="mt-3 rounded-md px-3 py-1.5 text-xs font-semibold text-white"
							style={{ backgroundColor: secondaryColor }}
						>
							RSVP
						</button>
					</div>
				</div>
			</div>

			<div className="flex items-center gap-3">
				<Button type="button" onClick={save} disabled={!dirty || updateColors.isPending}>
					{updateColors.isPending ? "Saving…" : "Save colors"}
				</Button>
				{updateColors.isError && <p role="alert" className="text-sm text-error-red">Could not save colors. Please try again.</p>}
				{updateColors.isSuccess && !dirty && <p role="status" className="text-sm text-slate-gray dark:text-[#cbd5e1]">Saved.</p>}
			</div>
		</div>
	);
}

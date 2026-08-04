import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from "react";
import { Link, type LinkProps } from "react-router-dom";

type CommonProps = {
	children: ReactNode;
	loading?: boolean;
	icon?: "arrow" | "external" | "none";
	className?: string;
};

type AsButton = CommonProps & ButtonHTMLAttributes<HTMLButtonElement> & { to?: undefined; href?: undefined };
type AsLink = CommonProps & Omit<LinkProps, "children" | "className"> & { href?: undefined };
type AsAnchor = CommonProps &
	Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "children" | "className"> & { to?: undefined; href: string };

type ButtonLikeProps = AsButton | AsLink | AsAnchor;

function ArrowIcon() {
	return (
		<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true" className="shrink-0">
			<path
				d="M3.5 8h9m0 0-3.5-3.5M12.5 8 9 11.5"
				stroke="currentColor"
				strokeWidth="1.5"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

function ExternalIcon() {
	return (
		<svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true" className="shrink-0">
			<path
				d="M6.5 3.5h6v6M12.5 3.5 3.5 12.5"
				stroke="currentColor"
				strokeWidth="1.5"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

function Spinner() {
	return (
		<svg
			className="size-4 shrink-0 animate-spin"
			viewBox="0 0 24 24"
			fill="none"
			aria-hidden="true"
		>
			<circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
			<path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z" />
		</svg>
	);
}

function renderContent(children: ReactNode, loading: boolean | undefined, icon: "arrow" | "external" | "none" | undefined) {
	return (
		<span className="inline-flex items-center justify-center gap-2">
			{loading && <Spinner />}
			{children}
			{!loading && icon === "arrow" && <ArrowIcon />}
			{!loading && icon === "external" && <ExternalIcon />}
		</span>
	);
}

function buildClassName(base: string, className: string | undefined) {
	return `group inline-flex min-h-11 min-w-11 items-center justify-center rounded-[10px] px-6 py-3 text-sm font-semibold transition-all duration-200 ease-[cubic-bezier(0.2,0.8,0.2,1)] hover:-translate-y-px focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-orange-400 disabled:pointer-events-none disabled:opacity-45 ${base} ${className ?? ""}`;
}

function renderAs(props: ButtonLikeProps, className: string) {
	// `className` must be destructured out here too (not just `children`/`loading`/
	// `icon`) — otherwise it lingers in `rest` and, since the spread below runs after
	// the JSX `className={className}` attribute, silently overwrites the fully-built
	// class string (background, text color, shadow, etc.) with just the caller's raw
	// className whenever one is passed.
	const { children, loading, icon, className: _callerClassName, ...rest } = props;

	if ("to" in props && props.to !== undefined) {
		const { to, ...linkRest } = rest as Omit<AsLink, "children" | "loading" | "icon" | "className">;
		return (
			<Link to={to} className={className} {...linkRest}>
				{renderContent(children, loading, icon)}
			</Link>
		);
	}

	if ("href" in props && props.href !== undefined) {
		const { href, ...anchorRest } = rest as Omit<AsAnchor, "children" | "loading" | "icon" | "className">;
		return (
			<a href={href} className={className} {...anchorRest}>
				{renderContent(children, loading, icon)}
			</a>
		);
	}

	const buttonRest = rest as Omit<AsButton, "children" | "loading" | "icon" | "className">;
	return (
		<button type="button" className={className} disabled={loading || buttonRest.disabled} {...buttonRest}>
			{renderContent(children, loading, icon)}
		</button>
	);
}

/**
 * Primary CTA (section 10.1): Get Started, Sign In, Create Account, Continue.
 * Rebranded orange (ADR-057) — was green under the LeagueLift palette.
 */
export function PrimaryButton(props: ButtonLikeProps) {
	return renderAs(
		props,
		buildClassName("bg-orange-500 text-white shadow-[0_12px_34px_rgba(242,96,12,0.30)] hover:bg-orange-400 active:bg-orange-600", props.className),
	);
}

/** Secondary dark button for use on navy backgrounds (section 10.2): See How It Works. */
export function SecondaryDarkButton(props: ButtonLikeProps) {
	return renderAs(
		props,
		buildClassName(
			"border border-white/25 bg-transparent text-white hover:border-orange-400/70",
			props.className,
		),
	);
}

/** Secondary light button for use on white/ice backgrounds (section 10.3). */
export function SecondaryLightButton(props: ButtonLikeProps) {
	return renderAs(
		props,
		buildClassName(
			"border border-navy-900 bg-white text-navy-900 hover:border-orange-500 hover:text-orange-600",
			props.className,
		),
	);
}

/** Gold emphasis button (section 10.4) — guided-onboarding / premium moments only. */
export function GoldButton(props: ButtonLikeProps) {
	return renderAs(
		props,
		buildClassName("bg-gold-500 text-navy-950 shadow-[0_12px_34px_rgba(244,183,64,0.28)] hover:bg-gold-400", props.className),
	);
}

/** Plain text button (section 10.5): Log In, Forgot password?, Learn more, Back. */
export function TextButton(props: ButtonLikeProps) {
	return renderAs(
		props,
		buildClassName(
			"min-h-0 min-w-0 rounded-md px-2 py-1 text-current underline-offset-4 hover:underline",
			props.className,
		),
	);
}

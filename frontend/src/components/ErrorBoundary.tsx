import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
	children: ReactNode;
}

interface State {
	hasError: boolean;
}

/**
 * Root-level safety net. Without this, an uncaught render error anywhere in the tree
 * unmounts the entire app (React's default with no boundary) and leaves the user on a
 * blank page with no way to recover short of guessing to reload — see LR-006/LAUNCH-READINESS.md.
 * This does not replace fixing the underlying error; it exists so a crash is always
 * recoverable instead of a dead end.
 */
export class ErrorBoundary extends Component<Props, State> {
	state: State = { hasError: false };

	static getDerivedStateFromError(): State {
		return { hasError: true };
	}

	componentDidCatch(error: Error, errorInfo: ErrorInfo) {
		console.error("Unhandled render error caught by ErrorBoundary", error, errorInfo);
	}

	render() {
		if (this.state.hasError) {
			return (
				<div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-navy-900 p-6 text-center text-white">
					<h1 className="font-heading text-2xl font-extrabold">Something went wrong.</h1>
					<p className="max-w-md text-slate-300">
						We hit an unexpected error. Reloading the page usually fixes this — if it keeps happening, contact
						support.
					</p>
					<button
						type="button"
						onClick={() => window.location.reload()}
						className="rounded-full bg-orange-600 px-5 py-2.5 font-semibold text-white hover:bg-orange-700"
					>
						Reload page
					</button>
				</div>
			);
		}

		return this.props.children;
	}
}

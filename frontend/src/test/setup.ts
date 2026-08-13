import { afterEach } from "vitest";
import "@testing-library/jest-dom/vitest";

// jsdom implements neither of these; components that check `prefers-reduced-motion`
// or scroll to an anchor (marketing header/homepage) need a stub, not the real thing.
if (!Element.prototype.scrollIntoView) {
	Element.prototype.scrollIntoView = () => {};
}

// AuthContext persists the session to sessionStorage (see auth/AuthContext.tsx) so a real
// sign-in in one test doesn't leave a session that leaks into a later test in the same file.
afterEach(() => {
	window.sessionStorage.clear();
});

if (!window.matchMedia) {
	window.matchMedia = (query: string) =>
		({
			matches: false,
			media: query,
			onchange: null,
			addListener: () => {},
			removeListener: () => {},
			addEventListener: () => {},
			removeEventListener: () => {},
			dispatchEvent: () => false,
		}) as MediaQueryList;
}

// HomeHeader tracks the active section with a real IntersectionObserver; jsdom has none.
if (!window.IntersectionObserver) {
	class MockIntersectionObserver implements IntersectionObserver {
		readonly root: Element | Document | null = null;
		readonly rootMargin = "";
		readonly thresholds: ReadonlyArray<number> = [];
		observe() {}
		unobserve() {}
		disconnect() {}
		takeRecords(): IntersectionObserverEntry[] {
			return [];
		}
	}
	window.IntersectionObserver = MockIntersectionObserver as unknown as typeof IntersectionObserver;
}

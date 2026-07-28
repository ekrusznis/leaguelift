import "@testing-library/jest-dom/vitest";

// jsdom implements neither of these; components that check `prefers-reduced-motion`
// or scroll to an anchor (marketing header/homepage) need a stub, not the real thing.
if (!Element.prototype.scrollIntoView) {
	Element.prototype.scrollIntoView = () => {};
}

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

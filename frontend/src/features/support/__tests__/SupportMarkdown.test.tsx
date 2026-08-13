import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SupportMarkdown } from "../SupportMarkdown";

describe("SupportMarkdown", () => {
	it("renders the supported formatting subset without interpreting raw HTML", () => {
		render(<SupportMarkdown body={"## Setup\n\n- First step\n- **Second step**\n\n<script>alert('x')</script>"} />);

		expect(screen.getByRole("heading", { name: "Setup" })).toBeInTheDocument();
		expect(screen.getByText("Second step").tagName).toBe("STRONG");
		expect(screen.getByText("<script>alert('x')</script>")).toBeInTheDocument();
		expect(document.querySelector("script")).toBeNull();
	});

	it("neutralizes unsupported link protocols", () => {
		render(<SupportMarkdown body={"[Unsafe](javascript:alert('x')) and [Safe](/help)"} />);

		expect(screen.getByRole("link", { name: "Unsafe" })).toHaveAttribute("href", "#");
		expect(screen.getByRole("link", { name: "Safe" })).toHaveAttribute("href", "/help");
	});

	it("embeds an image from a safe URL", () => {
		render(<SupportMarkdown body={"![Setup diagram](https://signed.example.com/diagram.png)"} />);

		const image = screen.getByRole("img", { name: "Setup diagram" });
		expect(image).toHaveAttribute("src", "https://signed.example.com/diagram.png");
	});

	it("renders a .mp4 or .mov embed as video rather than an image", () => {
		const { container } = render(
			<SupportMarkdown body={"![Walkthrough](https://signed.example.com/clip.mp4?X-Amz-Signature=abc)"} />,
		);

		const video = container.querySelector("video");
		expect(video).not.toBeNull();
		expect(video).toHaveAttribute("src", "https://signed.example.com/clip.mp4?X-Amz-Signature=abc");
		expect(container.querySelector("img")).toBeNull();
	});

	it("drops an image/video embed pointing at an unsafe URL", () => {
		render(<SupportMarkdown body={"![Bad](javascript:alert('x'))"} />);

		expect(document.querySelector("img")).toBeNull();
		expect(document.querySelector("video")).toBeNull();
	});
});

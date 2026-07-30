import { z } from "zod";

export const connectIcsFeedSchema = z.object({
	label: z.string().trim().min(1, "Label is required.").max(120),
	feedUrl: z.string().trim().url("Enter a valid http(s) URL.").max(2000),
	timezone: z.string().trim().min(1, "Timezone is required.").max(60),
});

export type ConnectIcsFeedFormValues = z.infer<typeof connectIcsFeedSchema>;

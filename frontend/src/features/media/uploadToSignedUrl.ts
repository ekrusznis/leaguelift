export class MediaUploadError extends Error {
	readonly status: number;

	constructor(status: number) {
		super(`Upload failed with status ${status}.`);
		this.name = "MediaUploadError";
		this.status = status;
	}
}

/**
 * PUTs a file directly to a presigned object-storage URL — deliberately bypasses
 * apiFetch (DESIGN-DOC.md section 11.3: the browser uploads directly to storage, the
 * LeagueLift API never proxies file bytes). No Authorization header, no JSON body,
 * and storage error responses are XML, not the LeagueLift ErrorResponse shape, so this
 * throws a small local error type instead of reusing ApiError.
 */
export async function uploadToSignedUrl(url: string, file: File, requiredHeaders: Record<string, string>): Promise<void> {
	const response = await fetch(url, {
		method: "PUT",
		headers: requiredHeaders,
		body: file,
	});
	if (!response.ok) {
		throw new MediaUploadError(response.status);
	}
}

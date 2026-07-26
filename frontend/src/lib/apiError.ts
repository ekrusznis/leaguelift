/** Mirrors the backend's standard error envelope (DESIGN-DOC.md section 13.3). */
export interface FieldError {
	field: string;
	message: string;
}

export interface ApiErrorBody {
	code: string;
	message: string;
	requestId: string;
	fieldErrors: FieldError[];
}

export class ApiError extends Error {
	readonly code: string;
	readonly requestId: string;
	readonly fieldErrors: FieldError[];
	readonly status: number;

	constructor(status: number, body: ApiErrorBody) {
		super(body.message);
		this.name = "ApiError";
		this.status = status;
		this.code = body.code;
		this.requestId = body.requestId;
		this.fieldErrors = body.fieldErrors;
	}
}

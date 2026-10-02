package com.fintech.wallet.common.error;

public enum ErrorCode {

	VALIDATION_ERROR(400, "Dữ liệu không hợp lệ"),
	INVALID_SIGNATURE(400, "Chữ ký không hợp lệ"),
	UNAUTHENTICATED(401, "Chưa xác thực"),
	TOKEN_EXPIRED(401, "Token đã hết hạn"),
	FORBIDDEN(403, "Không có quyền truy cập"),
	ACCOUNT_NOT_FOUND(404, "Không tìm thấy tài khoản"),
	NOT_FOUND(404, "Không tìm thấy tài nguyên"),
	DUPLICATE_REQUEST(409, "Yêu cầu đang được xử lý"),
	IDEMPOTENCY_CONFLICT(409, "Idempotency key đã dùng cho payload khác"),
	INSUFFICIENT_BALANCE(422, "Số dư tài khoản không đủ"),
	CURRENCY_MISMATCH(422, "Loại tiền không khớp"),
	FRAUD_BLOCKED(422, "Giao dịch bị chặn bởi rule engine"),
	ACCOUNT_LOCKED(423, "Tài khoản bị khoá"),
	RATE_LIMITED(429, "Vượt quá giới hạn yêu cầu"),
	UPSTREAM_UNAVAILABLE(503, "Service phụ thuộc không khả dụng");

	private final int httpStatus;
	private final String defaultMessage;

	ErrorCode(int httpStatus, String defaultMessage) {
		this.httpStatus = httpStatus;
		this.defaultMessage = defaultMessage;
	}

	public int httpStatus() {
		return httpStatus;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}

package com.example.CMS.Exception;

/**
 * The submitted password-reset code is wrong, expired, used up or never requested. Deliberately
 * one exception (and one message) for all cases so callers cannot tell them apart.
 */
public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException() {
        super("Mã OTP không hợp lệ hoặc đã hết hạn!");
    }
}

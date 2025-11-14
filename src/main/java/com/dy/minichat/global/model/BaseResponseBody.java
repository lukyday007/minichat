package com.dy.minichat.global.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BaseResponseBody {
    private String message = null;
    private Integer statusCode = null;

    private BaseResponseBody(Integer statusCode) {
        this.statusCode = statusCode;
    }

    private BaseResponseBody(Integer statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public static BaseResponseBody of(Integer statusCode, String message) {
        BaseResponseBody body = new BaseResponseBody();
        body.message = message;
        body.statusCode = statusCode;
        return body;
    }

    public boolean getStatus() {
        if (this.statusCode == null) {
            return false;
        }
        // 2xx (Success) 범위인지 확인
        return this.statusCode >= 200 && this.statusCode < 300;
    }}
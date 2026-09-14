package com.bilibili.okretro;

/** Test-only parser envelope, never included in the APK. */
public class GeneralResponse {
    public Object data;
    public GeneralResponse(Object data) { this.data = data; }
}

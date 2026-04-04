package com.livewell.dto

data class ApiResponse<T>(
    val code: Int = 200,
    val data: T,
    val msg: String = "success"
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(code = 200, data = data, msg = "success")
        }
        
        fun <T> success(data: T, msg: String): ApiResponse<T> {
            return ApiResponse(code = 200, data = data, msg = msg)
        }
        
        fun error(code: Int, msg: String): ApiResponse<Unit> {
            return ApiResponse(code = code, data = Unit, msg = msg)
        }
    }
}
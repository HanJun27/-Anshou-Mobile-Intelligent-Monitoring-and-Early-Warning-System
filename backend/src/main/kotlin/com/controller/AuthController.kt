package com.livewell.controller

import com.livewell.dto.ApiResponse
import com.livewell.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository
) {
    
    @PostMapping("/login")
    fun login(@RequestBody request: Map<String, String>): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val username = request["username"] ?: throw RuntimeException("用户名不能为空")
        val password = request["password"] ?: throw RuntimeException("密码不能为空")
        
        val user = userRepository.findByUsername(username)
            .orElseThrow { RuntimeException("用户名或密码错误") }
        
        if (user.password != password) {
            throw RuntimeException("用户名或密码错误")
        }
        
        val token = "mock-jwt-token-${UUID.randomUUID()}"
        
        val responseData = mapOf(
            "token" to token,
            "user" to mapOf(
                "id" to user.id,
                "username" to user.username,
                "name" to user.name
            )
        )
        
        return ResponseEntity.ok(ApiResponse.success(responseData, "登录成功"))
    }
    
    @PostMapping("/logout")
    fun logout(): ResponseEntity<ApiResponse<Unit>> {
        return ResponseEntity.ok(ApiResponse.success(Unit, "退出成功"))
    }
}
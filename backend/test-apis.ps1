# 测试后端 API 脚本

$baseUrl = 'http://localhost:8080/api'
Write-Host '=====================================' -ForegroundColor Cyan
Write-Host '开始测试后端接口...' -ForegroundColor Cyan
Write-Host '=====================================' -ForegroundColor Cyan
Write-Host ''

# 1. 测试登录接口
Write-Host '[1/8] 测试登录接口...' -ForegroundColor Yellow
try {
    $body = @{
        username = 'admin'
        password = '123456'
    } | ConvertTo-Json
    
    $loginResult = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -ContentType 'application/json'
    Write-Host '✓ 登录成功' -ForegroundColor Green
    Write-Host "  Token: $($loginResult.data.token)" -ForegroundColor Gray
    $token = $loginResult.data.token
} catch {
    Write-Host "✗ 登录失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 2. 测试用户列表
Write-Host '[2/8] 测试用户列表接口...' -ForegroundColor Yellow
try {
    $url = '$baseUrl/users?page=0&size=10'
    $result = Invoke-RestMethod -Uri $url -Method Get
    Write-Host '✓ 用户列表获取成功' -ForegroundColor Green
    Write-Host "  总用户数：$($result.data.total)" -ForegroundColor Gray
} catch {
    Write-Host "✗ 用户列表获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 3. 测试仪表板统计
Write-Host '[3/8] 测试仪表板统计接口...' -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "$baseUrl/dashboard/stats" -Method Get
    Write-Host '✓ 统计数据获取成功' -ForegroundColor Green
    Write-Host "  总用户数：$($result.data.totalUsers)" -ForegroundColor Gray
    Write-Host "  今日活跃：$($result.data.activeToday)" -ForegroundColor Gray
    Write-Host "  待处理警报：$($result.data.pendingAlerts)" -ForegroundColor Gray
} catch {
    Write-Host "✗ 统计数据获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 4. 测试警报趋势
Write-Host '[4/8] 测试警报趋势接口...' -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "$baseUrl/dashboard/alert-trend" -Method Get
    Write-Host '✓ 警报趋势获取成功' -ForegroundColor Green
    Write-Host "  数据点数：$($result.data.Count)" -ForegroundColor Gray
} catch {
    Write-Host "✗ 警报趋势获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 5. 测试用户状态分布
Write-Host '[5/8] 测试用户状态分布接口...' -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "$baseUrl/dashboard/user-status" -Method Get
    Write-Host '✓ 用户状态分布获取成功' -ForegroundColor Green
    Write-Host "  安全：$($result.data.safe) | 注意：$($result.data.warning) | 危险：$($result.data.danger)" -ForegroundColor Gray
} catch {
    Write-Host "✗ 用户状态分布获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 6. 测试实时警报
Write-Host '[6/8] 测试实时警报接口...' -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "$baseUrl/alerts/realtime" -Method Get
    Write-Host '✓ 实时警报获取成功' -ForegroundColor Green
    Write-Host "  未处理警报数：$($result.data.Count)" -ForegroundColor Gray
} catch {
    Write-Host "✗ 实时警报获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 7. 测试警报历史
Write-Host '[7/8] 测试警报历史接口...' -ForegroundColor Yellow
try {
    $url = '$baseUrl/alerts/history?page=0&size=10'
    $result = Invoke-RestMethod -Uri $url -Method Get
    Write-Host '✓ 警报历史获取成功' -ForegroundColor Green
    Write-Host "  总记录数：$($result.data.total)" -ForegroundColor Gray
} catch {
    Write-Host "✗ 警报历史获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''

# 8. 测试警报统计
Write-Host '[8/8] 测试警报统计接口...' -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "$baseUrl/alerts/stats" -Method Get
    Write-Host '✓ 警报统计获取成功' -ForegroundColor Green
    Write-Host "  总警报数：$($result.data.total)" -ForegroundColor Gray
    Write-Host "  已处理：$($result.data.processed)" -ForegroundColor Gray
    Write-Host "  处理率：$($result.data.rate)%" -ForegroundColor Gray
} catch {
    Write-Host "✗ 警报统计获取失败：$($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ''
Write-Host '=====================================' -ForegroundColor Cyan
Write-Host '测试完成！' -ForegroundColor Cyan
Write-Host '=====================================' -ForegroundColor Cyan
# Sentinel Local Helper
# Chay HTTP Server sieu nhe tren cong 45678 de lang nghe yeu cau mo App tu Sentinel Web

$port = 45678
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$port/")

try {
    $listener.Start()
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "  Sentinel Local Helper da khoi dong tren cong $port" -ForegroundColor Green
    Write-Host "  San sang mo File & Game tu trang chu Server Sentinel!" -ForegroundColor Yellow
    Write-Host "  (Thu nho cua so nay xuong thanh taskbar de su dung)" -ForegroundColor Gray
    Write-Host "==========================================================" -ForegroundColor Cyan
} catch {
    Write-Host ("Loi khi mo cong " + $port + ": " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        # Cho phep CORS de bat ky trang web Sentinel nao cung goi duoc
        $response.AddHeader("Access-Control-Allow-Origin", "*")
        $response.AddHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        $response.AddHeader("Access-Control-Allow-Headers", "Content-Type")

        if ($request.HttpMethod -eq "OPTIONS") {
            $response.StatusCode = 200
            $response.Close()
            continue
        }

        if ($request.Url.AbsolutePath -eq "/status") {
            $json = '{"status":"ok","message":"Sentinel Local Helper is running"}'
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentType = "application/json"
            $response.ContentLength64 = $buffer.Length
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
            $response.Close()
            continue
        }

        if ($request.Url.AbsolutePath -eq "/launch") {
            $path = $null

            if ($request.HttpMethod -eq "POST") {
                $reader = New-Object System.IO.StreamReader($request.InputStream, $request.ContentEncoding)
                $body = $reader.ReadToEnd()
                if ($body) {
                    try {
                        $parsed = $body | ConvertFrom-Json
                        $path = $parsed.path
                    } catch {}
                }
            }

            if (-not $path) {
                $path = $request.QueryString["path"]
            }

            if ($path) {
                $path = $path.Trim().Trim('"').Trim("'")
                Write-Host "[LAUNCH] Yeu cau mo: $path" -ForegroundColor Cyan

                if (Test-Path $path) {
                    Start-Process -FilePath $path
                    $json = '{"status":"success","message":"Da khoi chay thanh cong"}'
                    $response.StatusCode = 200
                } else {
                    Write-Host "[ERROR] Khong tim thay tep tin: $path" -ForegroundColor Red
                    $json = '{"status":"error","message":"Khong tim thay tep tin tren may"}'
                    $response.StatusCode = 404
                }
            } else {
                $json = '{"status":"error","message":"Thieu tham so path"}'
                $response.StatusCode = 400
            }

            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentType = "application/json"
            $response.ContentLength64 = $buffer.Length
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
            $response.Close()
            continue
        }

        $response.StatusCode = 404
        $response.Close()
    } catch {
        # Bo qua loi nhe va tiep tuc lang nghe
    }
}

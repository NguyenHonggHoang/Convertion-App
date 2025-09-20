# Converter Application

Ứng dụng web hoàn chỉnh bao gồm backend (Spring Boot), frontend (React.js), và các microservices (Flask) để hỗ trợ chuyển đổi đơn vị, chuyển đổi tiền tệ, quản lý người dùng, phân tích tin tức kinh tế, và cảnh báo tỷ giá.

## Tính năng chính

### Backend (Spring Boot)
- **Chuyển đổi đơn vị**: Hỗ trợ chuyển đổi giữa các đơn vị đo lường khác nhau
- **Chuyển đổi tiền tệ**: Chuyển đổi tiền tệ với tỷ giá real-time và dự đoán
- **Quản lý người dùng**: Đăng ký, đăng nhập, quản lý hồ sơ với JWT
- **Phân tích tin tức**: Thu thập và phân tích tin tức kinh tế
- **Cảnh báo tỷ giá**: Hệ thống cảnh báo thông minh
- **Redis Cache**: Cache tỷ giá và dữ liệu thường xuyên truy cập
- **PostgreSQL**: Lưu trữ dữ liệu người dùng, lịch sử chuyển đổi, tin tức

### Frontend (React.js)
- **Giao diện người dùng hiện đại**: Sử dụng Material-UI
- **Chuyển đổi đơn vị**: Hỗ trợ nhận diện giọng nói
- **Chuyển đổi tiền tệ**: Biểu đồ dự đoán và khuyến nghị thông minh
- **Phân tích tin tức**: Hiển thị tin tức với phân tích cảm xúc
- **Quản lý người dùng**: Đăng ký, đăng nhập, cập nhật hồ sơ

### Microservices (Flask)
- **Predict Service**: Dự đoán tỷ giá sử dụng AI/ML
- **Sentiment Service**: Phân tích cảm xúc tin tức
- **Crawl Service**: Thu thập tin tức từ các nguồn khác nhau

## Cấu trúc dự án

```
project-root/
├── backend/                       # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/converter/
│   │   │   │   ├── controller/    # REST Controllers
│   │   │   │   ├── service/       # Business Logic
│   │   │   │   ├── repository/    # Data Access
│   │   │   │   ├── entity/        # JPA Entities
│   │   │   │   ├── dto/          # Data Transfer Objects
│   │   │   │   ├── config/       # Configuration
│   │   │   │   └── security/     # JWT Security
│   │   │   └── resources/
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                      # React.js frontend
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   └── utils/
│   ├── package.json
│   └── Dockerfile
├── microservices/                 # Flask microservices
│   ├── predict-service/
│   │   ├── app.py
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   ├── sentiment-service/
│   │   ├── app.py
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   └── crawl-service/
│       ├── app.py
│       ├── requirements.txt
│       └── Dockerfile
├── docker-compose.yml
└── README.md
```

## Yêu cầu hệ thống

- Docker và Docker Compose
- Java 17 (cho development)
- Node.js 18 (cho development)
- Python 3.9 (cho development)

## Cài đặt và chạy

### Sử dụng Docker Compose (Khuyến nghị)

1. **Clone repository**:
   ```bash
   git clone <repository-url>
   cd converter-application
   ```

2. **Chạy toàn bộ hệ thống**:
   ```bash
   docker-compose up -d
   ```

3. **Truy cập ứng dụng**:
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Microservices:
     - Predict Service: http://localhost:5001
     - Sentiment Service: http://localhost:5002
     - Crawl Service: http://localhost:5003

### Development Mode

#### Backend
```bash
cd backend
./mvnw spring-boot:run
```

#### Frontend
```bash
cd frontend
npm install
npm start
```

#### Microservices
```bash
# Predict Service
cd microservices/predict-service
pip install -r requirements.txt
python app.py

# Sentiment Service
cd microservices/sentiment-service
pip install -r requirements.txt
python app.py

# Crawl Service
cd microservices/crawl-service
pip install -r requirements.txt
python app.py
```

## API Endpoints

### Authentication
- `POST /api/auth/register` - Đăng ký người dùng
- `POST /api/auth/login` - Đăng nhập

### User Management
- `GET /api/users/me` - Lấy thông tin hồ sơ
- `PUT /api/users/me` - Cập nhật hồ sơ

### Unit Conversion
- `POST /api/convert/unit` - Chuyển đổi đơn vị

### Currency Conversion
- `POST /api/convert/currency` - Chuyển đổi tiền tệ

### News Analysis
- `GET /api/news` - Lấy danh sách tin tức
- `POST /api/alerts` - Tạo cảnh báo

## Cấu hình

### Database
- PostgreSQL: `localhost:5432`
- Database: `converter_db`
- Username: `postgres`
- Password: `password`

### Redis Cache
- Host: `localhost`
- Port: `6379`

### Email Configuration
Cập nhật cấu hình email trong `backend/src/main/resources/application.properties`:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### JWT Configuration
Cập nhật JWT secret trong `backend/src/main/resources/application.properties`:
```properties
jwt.secret=your-secret-key-here-make-it-long-and-secure
```

## Tính năng AI/ML

### Dự đoán tỷ giá
- Sử dụng Prophet library để dự đoán tỷ giá
- Phân tích xu hướng thị trường
- Khuyến nghị thời điểm tối ưu để chuyển tiền

### Phân tích cảm xúc tin tức
- Sử dụng FinBERT để phân tích cảm xúc
- Phân loại tin tức theo chủ đề
- Tóm tắt tin tức tự động

### Hệ thống cảnh báo thông minh
- Cảnh báo dựa trên tỷ giá và xu hướng
- Tích hợp phân tích tin tức
- Gửi thông báo qua email

## Testing

### Backend Tests
```bash
cd backend
./mvnw test
```

### Frontend Tests
```bash
cd frontend
npm test
```

### Integration Tests
```bash
# Chạy toàn bộ hệ thống
docker-compose up -d

# Chạy tests
./run-integration-tests.sh
```

## Monitoring

### Health Checks
- Backend: http://localhost:8080/actuator/health
- Predict Service: http://localhost:5001/health
- Sentiment Service: http://localhost:5002/health
- Crawl Service: http://localhost:5003/health

### Logs
```bash
# Xem logs của tất cả services
docker-compose logs -f

# Xem logs của service cụ thể
docker-compose logs -f backend
docker-compose logs -f frontend
```

## Troubleshooting

### Common Issues

1. **Port conflicts**: Đảm bảo các port 3000, 8080, 5001-5003, 5432, 6379 không bị sử dụng
2. **Database connection**: Kiểm tra PostgreSQL container đã chạy
3. **Memory issues**: Tăng memory cho Docker nếu cần

### Local backend outside Docker (Windows)

If you run the backend from your IDE while Redis/Postgres run in Docker, use the local Spring profile so Redis host is 127.0.0.1 and scheduled jobs are disabled:

```powershell
# Start infra
docker compose up -d postgres redis

# Run backend with local profile from terminal
$env:SPRING_PROFILES_ACTIVE = "local"; cd backend; .\mvnw.cmd spring-boot:run
```

Or set the profile persistently for your user:

```powershell
setx SPRING_PROFILES_ACTIVE "local"
```

This avoids errors like "Connection refused: localhost:5003" when microservices aren't running locally and ensures Redis connectivity via 127.0.0.1:6379.

### Reset Database

```bash
docker-compose down -v
docker-compose up -d
```

## Contributing

1. Fork repository
2. Tạo feature branch
3. Commit changes
4. Push to branch
5. Tạo Pull Request

## License

MIT License

## Support

Nếu gặp vấn đề, vui lòng tạo issue trên GitHub repository.
